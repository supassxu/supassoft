
  public static boolean checkSparkCliResult(String type, String logString, String expectFile) {
    ArrayList<String> logArr = getSparkSqlResult(type, logString);
    ArrayList<String> expArr = getSparkSqlExpect("file", expectFile);

    int expSize = expArr.size();
    int logSize = logArr.size();
    int currIdx = 0;
    String sql = null;
    String[] filter =
      new String[] {"Owner:", "CreateTime:", "last_modified_by", "last_modified_time", "transient_lastDdlTime"};

    if (!logArr.get(currIdx).equalsIgnoreCase("spark.sql.hive.version=" + SparkTestEnv.hive_version)) {
      System.out.println("=======Error: hive version is not match: " + logArr.get(currIdx) + "============");
      return false;
    }
    currIdx++;

    for (int i = 0; i < expSize; i++) {
      if (expArr.get(i).startsWith("Running SQL: ")) {
        sql = expArr.get(i);
        continue;
      }

      if (currIdx >= logSize) {
        System.out.println("Error: sql result is too little in " + sql);
        return false;
      } else if (!expArr.get(i).equalsIgnoreCase(logArr.get(currIdx))) {
        if (sql.startsWith("Running SQL: desc formatted")) {
          if (filterInCliCheck(logArr.get(currIdx), expArr.get(i), filter)) {
            currIdx++;
            continue;
          }
        } else if (sql.startsWith("Running SQL: explain extended")) {
          String log = logArr.get(currIdx).replaceAll("#\\d+", "#").replaceAll("binaryformat\\),", "")
            .replaceAll("binaryformat\\)", "").replaceAll(",", "");
          String exp =
            expArr.get(i).replaceAll("#\\d+", "#").replaceAll("binaryformat\\),", "").replaceAll("binaryformat\\)", "")
              .replaceAll(",", "");
          if (log.equalsIgnoreCase(exp)) {
            currIdx++;
            continue;
          } else if ((exp.indexOf("[") != -1) && (log.startsWith(exp.substring(0, exp.indexOf("[")).trim()))) {
            currIdx++;
            continue;
          }
        }
        //hwx 309003 add
        else if (sql.contains("explain")) {
          String log = logArr.get(currIdx).replaceAll("#\\d+", "#").replaceAll("binaryformat\\),", "")
            .replaceAll("binaryformat\\)", "").replaceAll(",", "");
          String exp =
            expArr.get(i).replaceAll("#\\d+", "#").replaceAll("binaryformat\\),", "").replaceAll("binaryformat\\)", "")
              .replaceAll(",", "");
          if (log.equalsIgnoreCase(exp)) {
            currIdx++;
            continue;
          } else if ((exp.indexOf("[") != -1) && (log.startsWith(exp.substring(0, exp.indexOf("[")).trim()))) {
            currIdx++;
            continue;
          }
        }
        //x00356764
        else if(sql.startsWith("Running SQL: show broadcast tables"))
        {
          String log = logArr.get(currIdx).replaceAll(" ", "");
          String exp = expArr.get(i).replaceAll(" ", "");
          //String  replacestr = getOneByRex(log,"Statistics\\(\\d+");
          //String  replacestrexp = getOneByRex(exp,"Statistics\\(\\d+");
          log = log.replaceAll("Statistics\\(\\d+\\,\\d+\\)","");
          exp = exp.replaceAll("Statistics\\(\\d+\\,\\d+\\)","");

          log = log.replaceAll("#\\d+", "#").replaceAll("binaryformat\\),", "").replaceAll("binaryformat\\)", "").replaceAll(",", "");
          exp = exp.replaceAll("#\\d+", "#").replaceAll("binaryformat\\),", "").replaceAll("binaryformat\\)", "").replaceAll(",", "");

          if (log.equalsIgnoreCase(exp)) {
            currIdx++;
            continue;
          }
        }
        System.out.println("Error: not match in " + sql);
        System.out.println("===============Result=============");
        System.out.println(logArr.get(currIdx));
        System.out.println("===============Expect=============");
        System.out.println(expArr.get(i));
        return false;
      }

      currIdx++;
    }

    if (currIdx < logSize) {
      System.out.println("Error: sql result is too much in " + sql);
      return false;
    }
    return true;
  }

  public static boolean filterInCliCheck(String log, String exp, String[] filter) {
    int size = filter.length;
    for (int i = 0; i < size; i++) {
      if ((log.contains(filter[i])) && (exp.contains(filter[i]))) {
        return true;
      }
    }

    return false;
  }

  public static void cleanDatabase(String database) {
    // Sshcmd execCommand = new Sshcmd(clusterTopo.client_ip, clusterTopo.client_username, clusterTopo.client_pwd);
    //String result[] = RunSparkCmd.RunSparkSql("command", "show tables in " + database + ";", SparkTestEnv.client_baseConf + "2>xxdd.log");
    //String temp[] = result[2].split("\n");


    /*int len = temp.length;
    for (int i = 2; i < len; i++) {
        String tablename = getOneByRex(temp[i], "^.*\\s").replaceAll("\\t", ";");
        if (null != tablename)
            dropcmd += "drop table " + database + "." + tablename;
    }
    System.out.println("====================");
    System.out.println(dropcmd);

    if (!database.equals("default"))
         dropcmd += "drop database " + database + ";";
    if (dropcmd != "") {
        RunSparkCmd.RunSparkSql("command", dropcmd, SparkTestEnv.client_baseConf + "2>xxdd.log");
    }
    execCommand.exec("rm /" + clusterTopo.client_username + "/xxdd.log");*/

    String result[] = RunSparkCmd.RunBeeline("command", "show tables in " + database + ";");
    String temp[] = result[2].split("\n");
    int begin = FindNext(0, temp);
    begin = FindNext(begin, temp);
    int end = FindNext(begin, temp);
    String dropcmd = "";
    for (int i = begin + 1; i < end; i++) {
      String tablename = getOneByRex(temp[i], "^\\|.*\\s+\\|\\s+");
      tablename = tablename.replaceAll("\\|", "").replaceAll(" ", "");
      if (null != tablename)
        dropcmd += "drop table " + database + "." + tablename + ";";
    }

    System.out.println("====================");
    System.out.println(dropcmd);

    if (!database.equals("default"))
      dropcmd += "drop database if exists " + database + ";";
    if (dropcmd != "") {
      RunSparkCmd.RunBeeline("command", dropcmd);
    }
  }

  public static void admincleanDatabase(String database) {
    //        RunSparkCmd.RunBeeline("command", "set role admin;use test1db;drop tablea3;drop database test1db;");
    String resultdb[] = RunSparkCmd.RunBeeline("command", "show databases;");
    if (resultdb[2].contains(database)) {
      String result[] = RunSparkCmd.RunBeeline("command", "show tables in " + database + ";");
      String temp[] = result[2].split("\n");
      int begin = FindNext(0, temp);
      begin = FindNext(begin, temp);
      int end = FindNext(begin, temp);
      String dropcmd = "";
      String dropcmdview = "";
      for (int i = begin + 1; i < end; i++) {
        String tablename = getOneByRex(temp[i], "^\\|.*\\s+\\|\\s+");
        tablename = tablename.replaceAll("\\|", "").replaceAll(" ", "");
        if (null != tablename) {
          if (tablename.contains("_view")) {
            dropcmdview += "drop view " + database + "." + tablename + ";";
          }else if(tablename.contains("index")){
          }
          else {
            dropcmd += "drop table " + database + "." + tablename + ";";
          }
        }
      }

      System.out.println("====================");
      System.out.println(dropcmd);

      if (!database.equals("default"))
        dropcmd += "drop database " + database + ";";
      if (dropcmd != "") {
        RunSparkCmd.RunBeeline_admin("commandmore", "set role admin;" + dropcmdview + dropcmd);
      }
    }
  }

  public static String[] runSqlAndCheckResult(String runType, String source, String sqlCommand, String expFile) {
    String[] ret = new String[3];
    String result[] = null;
    boolean flag = false;

    if ("beeline".equalsIgnoreCase(runType)) {
      result = RunSparkCmd.RunBeeline(source, sqlCommand);
    } else {
      result = RunSparkCmd.RunSparkSql(source, sqlCommand, SparkTestEnv.client_baseConf);
    }

    ret[0] = result[0];
    ret[1] = "0";
    ret[2] = result[1];
    if (!"0".equals(ret[0])) {
      return ret;
    }

    if ("beeline".equals(runType)) {
      flag = CheckSparkResult.checkSqlResult(sqlCommand, result[2], expFile);
    } else {
      flag = CheckSparkResult.checkSparkCliResult("log", result[2], expFile);
    }

    if (!flag) {
      ret[1] = "1";
    }

    return ret;
  }

  public static int checkSparkSmallFileResult(String result, int num) {
    String smResult[] = getStringByRex(result, "\\++-\\(\\d+\\)");
    int len = smResult.length, sum = 0;
    for (int i = len - 1; i >= len - num; i--) {
      sum += Integer.parseInt(getOneByRex(smResult[i], "\\d+"));
    }
    return sum;
  }

  public static String getExpect(String result) {
    String realRes = "";
    String tmp = getOneByRex(result, "Debug.*Succeeded\n\n");
    realRes = realRes.replaceAll(tmp, "");
    return realRes;
  }

  public static boolean isStreaming(String para, String file) throws Exception {
    String pid;
    String time;
    int timeout = 20000;
    Thread.sleep(20000);
    while (timeout <= 100000) {
      Thread.sleep(5000);
      timeout = timeout + 5000;
      System.out.println(
        "================================================isStreaming================================================");
      pid = RunSparkCmd
        .runLinuxCmd("ps -ef | grep SparkSubmit | grep '" + para + "' | grep -v 'grep' | awk '{print $2}'")[2];
      if (pid == null)
        return false;
      time = getOneByRex(RunSparkCmd.runLinuxCmd("cat " + file)[2], "Time\\:\\s+\\d+\\s++ms\\n");
      if (!(time == null))
        return true;
    }
    return false;
  }

  public static void stopStreaming(String file) throws Exception {
    int timeout = 20000;
    Thread.sleep(20000);
    String result = "";
    while (timeout <= 100000) {
      Thread.sleep(5000);
      timeout = timeout + 5000;
      System.out.println(
        "================================================stopStreaming================================================");
      result = RunSparkCmd.runLinuxCmd("tail -n 12 " + file)[2];
      String tmp = getOneByRex(result, "chenjinsheng\\.,0");
      if (!(null == tmp) || !result.contains("chenjinsheng"))
        return;
    }
    return;
  }

  public static void GenerateBigFile() {
    Sshcmd execCommand = new Sshcmd(clusterTopo.client_ip, clusterTopo.client_username, clusterTopo.client_pwd);
    String resource_path =
      "/opt/DataSightBasePlatform_Automation/DataSightBasePlatform_TestCode_HQ/resources/spark/sqldata";
    System.out.println(
      "==============================================GenerateBigFile==============================================");
    execCommand.execAll("sh " + ConfigProvider.getValue("spark_test_file") + "/GenerateBigFile.sh " + resource_path);
  }

  /**
   * @Function:get active JSBCServer ip
   * @return
   */
  public static String getActiveThriftServerIp() {
    Sshcmd execCommand = new Sshcmd(clusterTopo.client_ip, clusterTopo.client_username, clusterTopo.client_pwd);
    String cmd = SparkTestEnv.enableEnv + "echo 'get /thriftserver/active_thriftserver' |  " + "zkCli.sh -server "
      + clusterTopo.quorumpeer[0] + ":" + ClusterTopo.ZK_CLIENT_PORT;
    String[] result = execCommand.execAll(cmd);

    System.out.println("THRIFT_PORT is " + ClusterTopo.THRIFT_PORT);
    String active = CheckSparkResult.getOneByRex(result[2], "\\d+\\.\\d+\\.\\d+\\.\\d++:" + ClusterTopo.THRIFT_PORT);
    System.out.println("active is " + active);
    active = CheckSparkResult.getOneByRex(active, "^\\d+\\.\\d+\\.\\d+\\.\\d+");
    System.out.println("active is " + active);
    return active;
  }

  /**
   * @Function:get active JSBCServer port
   * @return
   */
  public static String getActiveThriftServerPort() {
    Sshcmd execCommand = new Sshcmd(clusterTopo.client_ip, clusterTopo.client_username, clusterTopo.client_pwd);
    String cmd = SparkTestEnv.enableEnv + "echo 'get /thriftserver/active_thriftserver' |  " + "zkCli.sh -server "
      + clusterTopo.quorumpeer[0] + ":" + ClusterTopo.ZK_CLIENT_PORT;
    String[] result3 = execCommand.execAll(cmd);

    System.out.println("THRIFT_PORT is " + ClusterTopo.THRIFT_PORT);
    String activeport = CheckSparkResult.getOneByRex(result3[2], ClusterTopo.THRIFT_PORT);
    activeport = CheckSparkResult.getOneByRex(activeport, ClusterTopo.THRIFT_PORT);
    System.out.println("activeport is " + activeport);
    return activeport;
  }

  public static String getActiveThriftServerIpAfterRestart() throws Exception {
    String activethriftserver = null;
    int checktime = 0;
    System.out.println("THRIFT_PORT is " + ClusterTopo.THRIFT_PORT);
    do {
      Thread.sleep(5000);
      String cmd2 = SparkTestEnv.enableEnv + "echo 'get /thriftserver/active_thriftserver' |  " + "zkCli.sh -server "
        + clusterTopo.quorumpeer[0] + ":" + ClusterTopo.ZK_CLIENT_PORT;
      String[] result22 = execCommand.execAll(cmd2);
      activethriftserver =
        CheckSparkResult.getOneByRex(result22[2], "\\d+\\.\\d+\\.\\d+\\.\\d++:" + ClusterTopo.THRIFT_PORT);
      checktime = checktime + 10000;
    } while (null == activethriftserver && checktime <= 240000);
    System.out.println("activethriftserver is " + activethriftserver);
    activethriftserver = CheckSparkResult.getOneByRex(activethriftserver, "^\\d+\\.\\d+\\.\\d+\\.\\d+");
    System.out.println("activethriftserver is " + activethriftserver);
    return activethriftserver;
  }

  /**
   * @Function:To check whether a JDBC service on a ip is startted
   * @return
   */
  public static boolean checkThriftServerStart() throws Exception {
    String start = null;
    int time = 0;
    do {
      Thread.sleep(10000);
      String cmd1 = SparkTestEnv.enableEnv + "echo 'ls /thriftserver/leader_election' |  " + "zkCli.sh -server "
        + clusterTopo.quorumpeer[0] + ":" + ClusterTopo.ZK_CLIENT_PORT + "| grep latch";
      String[] result33 = execCommand.execAll(cmd1);
      start = CheckSparkResult.getOneByRex(result33[2], ",");
      cmd1 = SparkTestEnv.enableEnv + "echo 'ls /thriftserver/leader_election' |  " + "zkCli.sh -server "
        + clusterTopo.quorumpeer[0] + ":" + ClusterTopo.ZK_CLIENT_PORT;
      result33 = execCommand.execAll(cmd1);
      time = time + 10000;
    } while (null == start && time <= 240000);

    if (null == start) {
      return false;
    } else {
      return true;
    }
  }

  /**
   * @Function:To check whether a JDBC service on a ip is stopped
   * @return
   */
  public static boolean checkThriftServerStop(String ip) throws Exception {
    Sshcmd execCommand = new Sshcmd(ip, clusterTopo.secure_user, clusterTopo.secure_pwd);
    String[] result5;
    String stop = null;
    int waittime = 0;
    do {
      Thread.sleep(1000);
      result5 = execCommand.execAll("jps");
      stop = CheckSparkResult.getOneByRex(result5[2], "SparkSubmit");
      waittime = waittime + 1000;
    } while (null != stop && waittime <= 30000);

    if (null != stop) {
      return false;
    } else {
      return true;
    }
  }

  /*
 create by mWX314046
 stop streaming kafka data
  */
  public static void stopStreamkafka(String file) throws Exception {
    int timeout = 20000;
    Thread.sleep(20000);
    String result = "";
    while (timeout <= 100000) {
      Thread.sleep(5000);
      timeout = timeout + 5000;
      System.out.println(
        "================================================stopStreaming================================================");
      result = RunSparkCmd.runLinuxCmd("tail -n 12 " + file)[2];
      String tmp = getOneByRex(result, "chenjinsheng\\.,120");
      if (!(null == tmp) || !result.contains("chenjinsheng"))
        return;
    }
    return;
  }

  /*
     x
      */
  public static void admincleanDB(String database) {
    //        RunSparkCmd.RunBeeline("command", "set role admin;use test1db;drop tablea3;drop database test1db;");

    String result[] = RunSparkCmd.RunBeeline("command", "show tables in " + database + ";");
    String temp[] = result[2].split("\n");
    int begin = FindNext(0, temp);
    begin = FindNext(begin, temp);
    int end = FindNext(begin, temp);
    String dropcmd = "";
    for (int i = begin + 1; i < end; i++) {
      String tablename = getOneByRex(temp[i], "^\\|.*\\s+\\|\\s+");
      tablename = tablename.replaceAll("\\|", "").replaceAll(" ", "");
      if (null != tablename)
        dropcmd += "drop table " + database + "." + tablename + ";";
    }
    String dropview = "";
    for (int i = begin + 1; i < end; i++) {
      String tablename = getOneByRex(temp[i], "^\\|.*\\s+\\|\\s+");
      tablename = tablename.replaceAll("\\|", "").replaceAll(" ", "");
      if (null != tablename)
        dropview += "drop view " + database + "." + tablename + ";";
    }

    System.out.println("====================");
    System.out.println(dropcmd);

    if (!database.equals("default"))
      dropcmd += "drop database if exists " + database + ";";
    if (dropcmd != "") {
      RunSparkCmd.RunBeeline_admin("commandmore", "set role admin;" + dropview);
      RunSparkCmd.RunBeeline_admin("commandmore", "set role admin;" + dropcmd);
    }
  }

  public static String getHostnamebyIp(String ip) {
    Sshcmd broker = new Sshcmd(ip, clusterTopo.client_username, clusterTopo.client_pwd);
    String hostname = broker.execAll("cat /etc/HOSTNAME")[2].replaceAll("\\n", "");
    return hostname;
  }

  public static String checkSqlResult_CBO(String numFiles, String result) {
    int index = result.indexOf(numFiles);
    int tmpindex = index + 1000;
    char resultchar[] = result.toCharArray();
    char resultreturn[] = new char[15];
    int k = 0;
    for (int i = index + numFiles.length() + 1; i < tmpindex; i++) {
      if (!(resultchar[i] == ' ')) {
        resultreturn[k] = resultchar[i];
        k++;
      }
      if (k > 0) {
        if ((resultchar[i] == ' ')) {
          break;
        }
      }

    }
    String results = String.valueOf(resultreturn).trim();
    return results;
  }

  public static Boolean test_checkexplainresult_common(String result, String rex, int rexnum) {
    //        boolean flag = false;

    String[] matchStr = getStringByRex(result, rex);
    if (matchStr.length == rexnum) {
      return true;

    }

    System.out.print("=====MissMatching!!!========");
    System.out.print("=====the actual result is " + result + "=======");
    return false;
  }

  public static Boolean checklimitResult(String sqlCommand, String result, String expFile) {

    String exp = expFile;
    String sql = sqlCommand;
    ArrayList<String> ListSql = new ArrayList<String>();
    ArrayList<HashSet<String>> ListRet = new ArrayList<HashSet<String>>();
    ArrayList<String> ListSql1 = new ArrayList<String>();
    ArrayList<HashSet<String>> ListRet1 = new ArrayList<HashSet<String>>();
    String expstring = null;

    ResultLimitExtract(result, ListRet);
    ResultLimitExtract(exp, ListRet1);
    int length = ListRet.size();
    int length1 = ListRet1.size();
    expstring = ListRet1.get(0).toString();
    for (int i = 1; i < length1; i++) {
      expstring = expstring.concat(ListRet1.get(i).toString());
    }
    for (int i = 0; i < length; i++) {
       if(( !(ListRet.get(i).contains("ERROR"))) && (!(expstring.contains(ListRet.get(i).toString())) )) {
        System.out.println("===============Result is not matching with Except!!!!=============");
        System.out.println(ListRet.get(i));
        System.out.println("===============Expect is not matching with Result!!!!!=============");
        System.out.println(ListRet1.get(i));
        return false;
      }
    }
    return true;

  }
