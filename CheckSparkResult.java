package com.huawei.datasight.test.util.spark;

import com.huawei.datasight.test.util.cli.Sshcmd;
import com.huawei.datasight.test.util.cluster.ClusterTopo;
import com.huawei.datasight.test.util.config.ConfigProvider;
import org.apache.commons.lang.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by c00228924 on 2015/7/7.
 */
public class CheckSparkResult {
  static ClusterTopo clusterTopo = ClusterTopo.getClusterTopo();
  static Sshcmd execCommand = new Sshcmd(clusterTopo.client_ip, clusterTopo.client_username, clusterTopo.client_pwd);
  private static final boolean ClusterIsSecurity = Boolean.valueOf(ConfigProvider.getClusterIsSecurity());

  public static Boolean checkResultByMatch(String result, String rex, boolean type) {
    boolean flag = false;

    String[] matchStr = getStringByRex(result, rex);
    if (!(type ^ (matchStr.length > 0))) {
      flag = true;
    }

    System.out.print("=====MissMatching!!!========");
    System.out.print("=====the actual result is " + result + "=======");
    return flag;
  }

  public static Boolean checkResultFromFile(String file, String rex, boolean type) {
    String[] cmdResult = RunSparkCmd.runLinuxCmd("cat " + file);
    if (!"0".equals(cmdResult[0])) {
      return false;
    }

    return checkResultByMatch(cmdResult[2], rex, type);
  }

  public static Boolean checkSqlResult(String result, String expFile, boolean type) {
    String[] cmdResult = RunSparkCmd.runLinuxCmd("cat " + expFile);
    if (!"0".equals(cmdResult[0])) {
      return false;
    } else if (cmdResult[2].equals(result)) {
      return (true ^ type);
    } else {
      return (false ^ type);
    }
  }

  /*
   * x00356764 2015.8.28
   */
  public static int FindNext(int i, String[] a) {
    int j = i + 1;
    for (; j < a.length; j++) {
      if (a[j].contains("+")) {
        return j;
      }
    }
    return j;
  }

  /*
          x00356764  2015.11.5
         功能：过滤影响比较结果的信息
   */
   /**
   * @author: x00356764  2015.11.5
   * @Function：Filtering information that effecting comparison result
   * @return
   */
  public static String sqlResultFilterBeeline(String filterString) {
    String filter[] = {"Owner", "CreateTime", "last_modified_by", "last_modified_time", "transient_lastDdlTime"};
    int filterLen = filter.length;
    for (int i = 0; i < filterLen; i++) {
      if (filterString.contains(filter[i]))
        filterString = filterString.replaceAll("\\|.*\\|", "");
    }
    return filterString;
  }

  /*
   * x00356764 2015.8.28
   * 功能：提取某个sql语句执行结果中的sql语句，表头和表内容，剔除无关信息
   */
  /**
   * @author: x00356764 2015.8.28
   * @Function：Extract a sql statements during the sql statement execution results, the header and table content, weed out irrelevant information
   * @return
   */
  public static void ResultExtract(
    String type, String sqlCommandtemp, String result, ArrayList<String> ListSql, ArrayList<HashSet<String>> ListRet) {
    //将结果按行分开，逐句寻找命令，表头，结果内容
    result = result.replaceAll(";\\s\n", ";\n"); //2015-10-9 修改
    result = result.replaceAll("\\s\n", ""); //2015-10-9 修改
    String[] ResultSplit = result.split("\n");
    String[] sqlCommand = null;
    String replacestr="";
    int broadcastnum=0;

    if ("sqlFile" == type)
      sqlCommand = RunSparkCmd.runLinuxCmd("cat " + sqlCommandtemp)[2].split(";\n");
    else if ("command" == type)
      sqlCommand = sqlCommandtemp.split(";");
    else {
      System.out.println("type is error!");
      return;
    }

    int sqlCom_length = sqlCommand.length;
    int ReslutSplit_length = ResultSplit.length;
    int j = 0, count = 0;
    boolean flag = false;

    for (int i = 0; i < sqlCom_length; i++) {
      for (; j < ReslutSplit_length; j++) {
        //防止空格影响判断
        if (ResultSplit[j].replaceAll(" ", "").contains(sqlCommand[i].replaceAll(" ", ""))) {
          flag = true;
          sqlCommand[i] = sqlCommand[i] + ";";
          //找到命令下第一条带“+”行
          int idex = FindNext(j, ResultSplit);
          //命令执行失败
          if (idex >= ReslutSplit_length) {
            flag = false;
          }
          //在下一条带“+”行出现之前的都是表头
          idex++;
          while (idex < ReslutSplit_length && !ResultSplit[idex].contains("+-")) {
            sqlCommand[i] += ResultSplit[idex].replaceAll(" ", "");
            idex++;
          }
          sqlCommand[i] += ";";
          ListSql.add(count, sqlCommand[i]);

          idex++;
          HashSet<String> Headtemp = new HashSet<String>();
          broadcastnum=0;
          while (idex < ReslutSplit_length && !ResultSplit[idex].contains("+-")) {
            if (ListSql.get(count).contains("desc formatted")||ListSql.get(count).contains("describe formatted") ||ListSql.get(count).contains("describe extended")) {
              ResultSplit[idex] = sqlResultFilterBeeline(ResultSplit[idex]);
            } else if (ListSql.get(count).contains("explain extended")) {
              ResultSplit[idex] = ResultSplit[idex].replaceAll("#\\d+", "#");
              ResultSplit[idex] = ResultSplit[idex].replaceAll("binaryformat\\),", "");
              ResultSplit[idex] = ResultSplit[idex].replaceAll("binaryformat\\)", "");
              ResultSplit[idex] = ResultSplit[idex].replaceAll(",", "");
              if (ResultSplit[idex].indexOf("[") != -1) {
                ResultSplit[idex] = ResultSplit[idex].substring(0, ResultSplit[idex].indexOf("["));
              }
            }
            //hwx 309003 add this
            else if (ListSql.get(count).contains("explain")) {
              ResultSplit[idex] = ResultSplit[idex].replaceAll("#\\d+", "#");
              ResultSplit[idex] = ResultSplit[idex].replaceAll("binaryformat\\),", "");
              ResultSplit[idex] = ResultSplit[idex].replaceAll("binaryformat\\)", "");
              ResultSplit[idex] = ResultSplit[idex].replaceAll(",", "");
              if (ResultSplit[idex].indexOf("[") != -1) {
                ResultSplit[idex] = ResultSplit[idex].substring(0, ResultSplit[idex].indexOf("["));
              }
            }
            else if (ListSql.get(count).contains("broadcast"))
            {
              ResultSplit[idex] = ResultSplit[idex].replaceAll(" ", "");
              //replacestr = getOneByRex(ResultSplit[idex],"Statistics\\(\\d+");
             // if (null !=replacestr)
              //{
              ResultSplit[idex] = ResultSplit[idex].replaceAll("Statistics\\(\\d+\\,\\d+\\)","");
             // }
              ResultSplit[idex] = ResultSplit[idex].replaceAll("#\\d+", "#");
              ResultSplit[idex] = ResultSplit[idex].replaceAll("binaryformat\\),", "");
              ResultSplit[idex] = ResultSplit[idex].replaceAll("binaryformat\\)", "");
              ResultSplit[idex] = ResultSplit[idex].replaceAll(",", "");
              if(ResultSplit[idex].contains("SavedBroadcastTables"))
                broadcastnum++;
            }
            Headtemp.add(ResultSplit[idex].replaceAll(" ", "").replaceAll("\t", ""));
            idex++;
          }
          if (ListSql.get(count).contains("broadcast")) {
            Headtemp.add("broadcastnum:" + Integer.toString(broadcastnum));
          }
          ListRet.add(count, Headtemp);

          count++;
          j = idex + 1;
          break;
        }
      }

      if (false == flag)
        break;
    }

  }

  /*
   * by x00356764 2015.8.31
   * 功能：判断目标字符串s是否包含keyWord中的元素
   */
  /**
   * @author: x00356764 2015.8.31
   * @Function：Judge whether the target string s included keyWord elements
   * @return
   */
  public static Boolean isContains(String s, String[] keyWord) {
    int i = 0, length = keyWord.length;
    for (i = 0; i < length; i++) {
      if (s.contains(keyWord[i]))
        return true;
    }

    return false;
  }
    /*
     *   by x00356764 2015.8.29
     *   要求sqlCommand 是文件路径
     *   功能：对sql语句执行结果和预期做匹配并输出不匹配的项
     */
  /**
   * @author: x00356764 2015.8.29
   * @Function：Expectations of sql statement execution results and do match, and output the item which does not match  
   * @param sqlCommand  It is required that the sqlCommand is a file path
   * @return
   */

  public static Boolean checkSqlResult(String sqlCommand, String result, String expFile) {

    String exp = RunSparkCmd.runLinuxCmd("cat " + expFile)[2];
    ArrayList<String> ListSql = new ArrayList<String>();
    ArrayList<HashSet<String>> ListRet = new ArrayList<HashSet<String>>();
    ArrayList<String> ListSql1 = new ArrayList<String>();
    ArrayList<HashSet<String>> ListRet1 = new ArrayList<HashSet<String>>();

    //Edit by ywx397348 on 2016/10/11   add if .
    String sql = "";
    if (sqlCommand.contains(SparkTestEnv.SQL_PATH)) {
      String resultSql[] = RunSparkCmd.runLinuxCmd("cat " + sqlCommand);
      sql = resultSql[2].replaceAll("\n", "");
//      ResultExtract("sqlFile", sql, result , ListSql , ListRet);
//      ResultExtract("sqlFile", sql , exp , ListSql1 , ListRet1);
      ResultExtract("command", sql, result, ListSql, ListRet);
      ResultExtract("command", sql, exp, ListSql1, ListRet1);
    }else  if (sqlCommand.contains(SparkTestEnv.SQL_PATH_2_1)) {
      String resultSql[] = RunSparkCmd.runLinuxCmd("cat " + sqlCommand);
      sql = resultSql[2].replaceAll("\n", "");
//      ResultExtract("sqlFile", sql, result , ListSql , ListRet);
//      ResultExtract("sqlFile", sql , exp , ListSql1 , ListRet1);
      ResultExtract("command", sql, result, ListSql, ListRet);
      ResultExtract("command", sql, exp, ListSql1, ListRet1);
    }
    else {
      sql = sqlCommand;
      ResultExtract("command", sql, sql + "\n" +  result, ListSql, ListRet);
      ResultExtract("command", sql, exp, ListSql1, ListRet1);
    }

    int length = ListSql.size(), length1 = ListSql1.size();
    int len = length > length1 ? length1 : length;

    for (int i = 0; i < len; i++) {
      if ((!(ListSql.get(i).equalsIgnoreCase(ListSql1.get(i))) && !isContains(ListSql.get(i), SparkTestEnv.key_word))
        || !(ListRet.get(i).equals(ListRet1.get(i)))) {
        if (!isContains(ListSql.get(i), SparkTestEnv.key_word))
          System.out.println("===============Result is not matching with Except!!!!=============");
        System.out.println(ListSql.get(i));
        System.out.println(ListRet.get(i));
        System.out.println("===============Expect is not matching with Result!!!!!=============");
        System.out.println(ListSql1.get(i));
        System.out.println(ListRet1.get(i));
        return false;
      }
    }

    if (length > length1) {
      for (int j = len; j < length; j++) {
        System.out.println("===============Result is not matching with Except!!!!=============");
        System.out.println(ListSql.get(j));
        System.out.println(ListRet.get(j));
      }
      System.out.println("===============Expect is Too Short,Expect is not matching with Result!!!!!=============");
      return false;
    }

    if (length < length1) {
      System.out.println("===============Result is Too Short,Result is not matching with Except!!!!=============");
      for (int j = len; j < length1; j++) {
        System.out.println("===============Expect is not matching with Result!!!!!=============");
        System.out.println(ListSql1.get(j));
        System.out.println(ListRet1.get(j));
      }
      return false;
    }

    return true;

  }

  /*
   * by x00356764 2015.8.31
   */
  public static void SqlErrorExtract(String type, String object, ArrayList<String> ErrorList) {
    String[] objectRow = null;
    String[] keyW = {"Error:", "Exception:"};  //Exception:
    if ("File" == type)
      objectRow = RunSparkCmd.runLinuxCmd("cat " + object)[2].split("\n");
    else if ("string" == type)
      objectRow = object.split("\n");
    else {
      System.out.println("type is error!");
    }
    int length = objectRow.length, i = 0;

    for (i = 0; i < length; i++) {
      if (isContains(objectRow[i], keyW)) {
        int id = objectRow[i].indexOf("Exception:");
        int idx = objectRow[i].length();
        String ext = "";
        ext += objectRow[i].substring(id, idx);
        ext = ext.replaceAll("\\(state=,code=0\\)", "");
        ext = ext.replaceAll("\\d+", "");
        ext = ext.replaceAll("line", "");
        ext = ext.replaceAll("pos", "");
        ext = ext.replaceAll(" ", "");
        ext = ext.replaceAll(":", "");
        ext = ext.replaceAll("vm", "");
        ext = ext.replaceAll("bi", "");
        ext = ext.replaceAll("-", "");
        //ext = ext.replaceAll("localhost","");
        ext = ext.replaceAll("\\(.*\\)", "");
        ErrorList.add(ext);
      }

    }

  }

  public static Boolean checkSqlError(String result, String expFile) {
    ArrayList<String> extRet = new ArrayList<String>();
    ArrayList<String> extExp = new ArrayList<String>();
    SqlErrorExtract("string", result, extRet);

    SqlErrorExtract("File", expFile, extExp);

    int length = extRet.size();
    if (extExp.size() != extRet.size())
      return false;
    else {
      for (int i = 0; i < length; i++) {
        if (!extRet.get(i).equals(extExp.get(i))) {
          System.out.println("============ErrorResult=============");
          System.out.println(extRet.get(i));
          System.out.println("============ErrorExpect=============");
          System.out.println(extExp.get(i));
          return false;
        }
      }
    }
    System.out.println("============ErrorResult=============");
    System.out.println(extRet.get(0));
    System.out.println("============ErrorExpect=============");
    System.out.println(extExp.get(0));
    return true;
  }

  public static Boolean checkDynmicallocResult(String stdErr, String stdOut, String expect, String[] executorExpect) {
    Boolean outPut = checkWordCountOutput(stdOut, expect);
    if (!outPut) {
      System.out.println("==============resultError==================");
      return false;
    }

    Boolean executor = checkDynmicallocExecutorInfo(stdErr, executorExpect);
    return executor;
  }

  public static Boolean checkWordCountOutput(String result, String expect) {
    HashMap<String, Integer> retHash = new HashMap<String, Integer>();
    HashMap<String, Integer> retExpectHash = new HashMap<String, Integer>();

    String[] resultArr = getStringByRex(result, "=====\\s+\\d+:\\d+");
    if ((resultArr.length == 0) && (expect == null)) {
      return true;
    } else if ((resultArr.length > 0) && (expect != null)) {
      //String[] expectArr = expect.split(",");
      String[] expectArr = expect.split("\n");
      if (resultArr.length != expectArr.length) {
        System.out.println("the size of result(output) is different with expect!");
        return false;
      }

      retHash = changeArrayToHashmap(resultArr);
      retExpectHash = changeArrayToHashmap(expectArr);
    } else {
      System.out.println("the size of result(output) is different with expect!");
      return false;
    }

    if (!retHash.equals(retExpectHash)) {
      System.out.println("the result(output) is not match witch expect!");
      return false;
    }

    return true;
  }

  public static Boolean checkDynmicallocExecutorInfo(String result, String[] expect) {
    String register = "";
    List<String> registerArr = new ArrayList(10);

    // get request info and remove info , due to compare with expect
    String executorLog = StringUtils.join(getStringByRex(result, SparkTestEnv.dynmicalloc_rex_all), "\n");
    String registerRex = "(" + SparkTestEnv.dynmicalloc_rex_request + ")|(" + SparkTestEnv.dynmicalloc_rex_remove + ")";
    String[] registerTemp = getStringByRex(executorLog, registerRex);
    String temp = null;
    for (int i = 0; i < registerTemp.length; i++) {
      temp = getOneByRex(registerTemp[i], SparkTestEnv.dynmicalloc_rex_request);
      int size = registerArr.size();
      if (temp != null) {
        temp = getOneByRex(temp, "\\d+");
        if(size == 0){
          registerArr.add("1");
        }
        else if (!registerArr.get(size -1).startsWith("-")){
          registerArr.set(size - 1, String.valueOf(Integer.valueOf(registerArr.get(size -1)) + Integer.valueOf(temp)));
        }
        else
        {
          registerArr.add(temp);
        }
      } else {
        temp = getOneByRex(registerTemp[i], SparkTestEnv.dynmicalloc_rex_removetime);
        temp = getOneByRex(temp, "\\d+");
        if ((size > 0) && (registerArr.get(size - 1).matches("-\\d+\\(\\d+\\)"))) {
          String[] old = getStringByRex(registerArr.get(size - 1), "\\d+");
          if (temp.equals(old[1])) {
            registerArr.set(size - 1, "-" + (Integer.valueOf(old[0]) + 1) + "(" + old[1] + ")");
          } else {
            System.out.println("the time of remove executor is different : " + temp + " <-> " + old[1]);
            return false;
          }
        } else {
          registerArr.add("-1(" + temp + ")");
        }
      }
    }
    register = StringUtils.join(registerArr, ",");

    // get all the executor id , and compare with expect
    HashMap<String, Integer> resultExecutor = new HashMap<String, Integer>();
    HashMap<String, Integer> expectExecutor = new HashMap<String, Integer>();
    String executorTemp = StringUtils.join(getStringByRex(executorLog, SparkTestEnv.dynmicalloc_rex_executor), "\n");
    resultExecutor = changeArrayToHashmap(getStringByRex(executorTemp, "\\d+"));

    for (int n = 1; n <= Integer.valueOf(expect[1]); n++) {
      expectExecutor.put(String.valueOf(n), 1);
    }

    // if ((register.equals(expect[0]))&&(resultExecutor.equals(expectExecutor)))
    if ((register.equals(expect[0])) && (resultExecutor.size() == expectExecutor.size())) {
      return true;
    } else if (!register.equals(expect[0])) {
      System.out.println("=================register-matchError=================");
      System.out.println(register);
    } else {
      System.out.println("=================executor-matchError=================");
    }

    return false;
  }

  public static String[] getStringByRex(String input, String rex) {
    List<String> result = new ArrayList(10);

    Pattern pattern = Pattern.compile(rex);
    Matcher match = pattern.matcher(input);

    while (match.find()) {
      result.add(match.group(0));
    }

    int size = result.size();
    String[] matchArr = result.toArray(new String[size]);

    return matchArr;
  }

  public static String getOneByRex(String input, String rex) {
    String result = null;
    Pattern pattern = Pattern.compile(rex);
    Matcher match = pattern.matcher(input);

    if (match.find()) {
      result = match.group(0);
    }

    return result;
  }
