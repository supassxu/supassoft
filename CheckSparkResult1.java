
  public static HashMap<String, Integer> changeArrayToHashmap(String[] arr) {
    HashMap<String, Integer> retHash = new HashMap<String, Integer>();
    for (int i = 0; i < arr.length; i++) {
      if (retHash.containsKey(arr[i])) {
        retHash.put(arr[i], retHash.get(arr[i]) + 1);
      } else {
        retHash.put(arr[i], 1);
      }
    }

    return retHash;
  }

  public static String getContainerLog(String clientFile, String type, String instance) {
    String appStr = null;
    String appIp = null;
    String appHost = null;
    String[] result = null;

    String clientLog = clientFile;
    if ("file".equalsIgnoreCase(type)) {
      String[] temp = RunSparkCmd.runLinuxCmd("cat " + clientFile);
      if (RunSparkCmd.checkCmdResult(temp[0])) {
        clientLog = temp[2];
      } else {
        return null;
      }
    }

    try {
      String logPath = null;
      appStr = getOneByRex(clientLog, SparkTestEnv.application_rex_id);
      if ("clusterDriver".equalsIgnoreCase(instance)) {
        appIp = getOneByRex(getOneByRex(clientLog, SparkTestEnv.application_rex_ip), "\\d+\\.\\d+\\.\\d+\\.\\d+");
        appHost = clusterTopo.IPHostMapping.get(appIp);
        //                appHost = getOneByRex(RunSparkCmd.runLinuxCmd("cat /etc/hosts")[2] , appIp + ".+").split(" ")[1]
        //                        .replaceAll("\\s+" , "");
        logPath = SparkTestEnv.application_rex_container_path + appStr + "/" + appHost + "_*";
      } else if ("containerAll".equalsIgnoreCase(instance)) {
        logPath = SparkTestEnv.application_rex_container_path + appStr + "/" + "*";
      } else {
        System.out.println("Error in getContainerLog: instance is not exists!");
        return null;
      }

      result = RunSparkCmd.runHdfsCmd("-cat", logPath);
    } catch (Exception e) {
      e.printStackTrace();
      return null;
    }

    return result[2];
  }

  public static boolean backUpAndModifyConfig(
    String file, String type, String[] modifyArr, String ip, String username, String password) {
    boolean flag = false;
    Sshcmd execCommand = new Sshcmd(ip, username, password);

    try {
      execCommand.execAll("cp " + file + " " + file + ".bak");
      String confLog = execCommand.execAll("cat " + file)[2];
      for (int n = 0; n < modifyArr.length; n++) {
        String conf = modifyArr[n].split("=")[0].replaceAll(" ", "");
        if (getOneByRex(confLog, "(^|\\s)" + conf + "(\\s|=).*") != null) {
          if (getOneByRex(confLog, "\\n" + conf + "(\\s|=).*") != null) {
            confLog = confLog.replaceAll("\\n" + conf + "(\\s|=).*", "\n" + modifyArr[n]);
          } else {
            confLog = confLog.replaceAll("\\s" + conf + "(\\s|=).*", " " + modifyArr[n]);
          }
        } else {
          if ("shell".equalsIgnoreCase(type)) {
            confLog += "\nexport " + modifyArr[n];
          } else if ("conf".equalsIgnoreCase(type)) {
            confLog += "\n" + modifyArr[n];
          } else {
            System.out.println("Error in backUpAndModifyConfig: the type is no supported!");
            return false;
          }
        }
      }

      if (ClusterIsSecurity) {
        confLog = confLog.replaceAll("\\$", "\\\\\\$");
      }
      execCommand.execAll("echo \"" + confLog + "\" > " + file);
      execCommand.execAll("chmod 777 " + file);
    } catch (Exception e) {
      e.printStackTrace();
      return false;
    }

    return true;
  }

  public static boolean recoveryConfig(String file, String ip, String username, String password) {
    boolean flag = false;
    Sshcmd execCommand = new Sshcmd(ip, username, password);

    String[] result = execCommand.execAll("mv " + file + ".bak " + file);
    if (RunSparkCmd.checkCmdResult(result[0])) {
      execCommand.execAll("chmod 777 " + file);
      return true;
    }

    return false;
  }

  /*
   * created by x00356764
   */
  public static void modifyExecutorLogLevel(String logLevel, String[] ipArr, String user, String password) {
    String file = ClusterTopo.SPARK_LOG4J_FILE;
    String[] cfArr = {"log4j.rootCategory=" + logLevel + ", console"};
    String[] cfArrFM = {"log4j.rootCategory=" + logLevel + ", sparklog"};

    int i = 0, len = ipArr.length;
    for (i = 0; i < len; i++) {
      if (!ClusterIsSecurity)
        backUpAndModifyConfig(file, "conf", cfArr, ipArr[i], user, password);
      else
        backUpAndModifyConfig(file, "conf", cfArrFM, ipArr[i], user, password);
    }
  }

  /*
   * created by x00356764
   */
  public static void recoveryExecutorLogLevel(String[] ipArr, String user, String password) {
    String file = ClusterTopo.SPARK_LOG4J_FILE;

    int i = 0, len = ipArr.length;
    for (i = 0; i < len; i++) {
      recoveryConfig(file, ipArr[i], user, password);
    }
  }

  public static boolean checkPySparkResult(String Result, String Expect, String rex) {
    String[] result = Result.split("\n");
    String[] expect = Expect.split("\n");
    //运行结果提取
	//The running result extraction
    int i = 0;
    ArrayList<String> result_tmp = new ArrayList<String>();
    for (i = 0; i < result.length; i++) {
      if (result[i].contains(rex)) {
        result_tmp.add(result[i]);
      }

    }
    //运行结果与预期比较
	//Running results compared with the expected

    if (result_tmp.size() != expect.length)
      return false;

    for (i = 0; i < expect.length; i++) {
      if (!result_tmp.get(i).equals(expect[i])) {
        return false;
      }
    }

    return true;
  }

  /*
   * created by x00356764
   */
  public static String getConfForDynmicAlloc(
    int min, int max, int init, int removeTimeout, int addTimeout, int secondAddTimeout) {
    String confStr = " --conf spark.shuffle.service.enabled=true --conf spark.shuffle.service.port="
      + SparkTestEnv.spark_external_shuffle_port + " ";
    confStr += " --conf spark.dynamicAllocation.enabled=true ";
    confStr += " --conf spark.dynamicAllocation.minExecutors=" + String.valueOf(min)
      + "  --conf spark.dynamicAllocation.maxExecutors=" + String.valueOf(max) + " ";
    confStr += " --conf spark.dynamicAllocation.executorIdleTimeout=" + String.valueOf(removeTimeout) + " ";
    confStr += " --conf spark.dynamicAllocation.schedulerBacklogTimeout=" + String.valueOf(addTimeout) + " ";

    if ((init > 0) || (init == -2))
      confStr += " --conf spark.dynamicAllocation.initialExecutors=" + String.valueOf(init) + " ";
    else if (ClusterIsSecurity)
      confStr += " --conf spark.dynamicAllocation.initialExecutors=" + String.valueOf(min) + " ";

    if (secondAddTimeout > 0)
      confStr +=
        " --conf spark.dynamicAllocation.sustainedSchedulerBacklogTimeout=" + String.valueOf(secondAddTimeout) + " ";

    return confStr;
  }

  /*
   * created by x00356764
   */
  public static String getWordcountExpect(String file, int num, boolean type) {
    String str = "=====" + file + ":\n";
    String temp = "(xxxxxxxxxxxxxxxxxxxxxxx," + String.valueOf(num) + ")\n";
    temp += "(zzzzzzzzzzzzzzzzzz," + String.valueOf(num) + ")\n";
    temp += "(vvvvvvvvvvvvvvvvvvvv," + String.valueOf(num) + ")\n";
    temp += "(ssssssssssss," + String.valueOf(num) + ")\n";
    temp += "(tttttttttttttttttttttttttttttt," + String.valueOf(num) + ")\n";
    temp += "(uuuuuuuuuuuuuuuuuuu," + String.valueOf(num) + ")\n";
    temp += "(yyyyyyyyyyyyyyyyyyyyy," + String.valueOf(num) + ")\n";
    temp += "(wwwwwwwwwwwwwwwwwwwww," + String.valueOf(num) + ")\n";
    if (type)
      str += temp.replaceAll("\n", "");
    else
      str += temp;

    str += "(sssssssssss," + String.valueOf(num) + ")\n";
    return str;
  }

  /*
   * x00356764
   */
  public static void killExecutorBydriverlog(String file) {
    ArrayList ipArr = new ArrayList();
    String log = "";
    //        String hostLog = "";
    log = RunSparkCmd.runLinuxCmd("cat " + file)[2];
    //        hostLog = RunSparkCmd.runLinuxCmd("cat /etc/hosts")[2];

    // get the ip where executor running
    String appStr = getOneByRex(log, "application\\_\\d+\\_\\d+");
    //        System.out.println("11111" + appStr);
    String tt = StringUtils.join(getStringByRex(log, "Registered\\s++executor.*\\n"), "\n");

    for (int i = 0; i < clusterTopo.node_num; i++) {
      if (tt.contains(clusterTopo.node_host[i])) {
        ipArr.add(clusterTopo.node_ip[i]);
      }
    }

    //
    //        System.out.println("22222" + tt);
    //        String[] arr = getStringByRex( tt , "\\@-*\\:");
    //        String temp = "";
    //        int i = 0 , len = arr.length;
    //        System.out.println("33333" + len);
    //        for ( i = 0 ; i < len ; i++ )
    //        {
    //            System.out.println("4444" + arr[i]);
    //            String reg = "\\d+\\.\\d+\\.\\d+\\.\\d+\\s+" + arr[i].substring(1 , (arr[i].indexOf(":"))) + "\\n";
    //            temp += getOneByRex( hostLog , reg);
    //            System.out.println("5555" + temp);
    //        }

    //        ipArr =  getStringByRex ( temp , "\\d+\\.\\d+\\.\\d+\\.\\d+");

    String ip;
    Iterator<String> itr = ipArr.iterator();
    while (itr.hasNext()) {
      ip = itr.next();
      System.out.println("will kill executor at " + ip);
      Sshcmd execTmp = new Sshcmd(ip, ClusterTopo.ant_script_machine_username, ClusterTopo.ant_script_machine_pwd);
      String cmd =
        "ps -ef | grep CoarseGrainedExecutorBackend | grep " + appStr + " | grep -v stderr | awk '{print $2}'";
      String pid = execTmp.execAll(cmd)[2];
      execTmp.execAll("kill -9 " + pid);
    }
  }

  /*
   * created by x00356764
   */
  public static String stopAppByDriverLog(String file, String paras, String type) {
    String log = "";
    if (type == "file") {
      log = RunSparkCmd.runLinuxCmd("cat " + file)[2];
      //RunSparkCmd.runLinuxCmd("rm " + file);
    } else
      log = file;

    String appStr = getOneByRex(log, "application\\_\\d+\\_\\d+");

    RunSparkCmd.runLinuxCmd(SparkTestEnv.enableEnv + "yarn application -kill " + appStr);
    String pid = RunSparkCmd
      .runLinuxCmd("ps -ef | grep SparkSubmit | grep '" + paras + "' | grep -v 'grep' | awk '{print $2}'")[2];
    RunSparkCmd.runLinuxCmd("kill -9 " + pid);

    return log;
  }

  public static boolean checkStreamingResult(String file, String type, int min, int max, String[] expect) {
    String reg = "-------------------------------------------\n";
    String reg1 = "Time\\:\\s+\\d+\\s++ms\\n";
    String a = "";
    if (type == "file")
      a = RunSparkCmd.runLinuxCmd("cat  " + file)[2];
    else
      a = file;
    String a1 = a.replaceAll(reg, "");
    String[] b = a1.split(reg1);

    String[] temp = {};
    int count = 0;
    HashMap<String, Integer> result = new HashMap<String, Integer>();
    HashMap<String, Integer> expt = new HashMap<String, Integer>();
    String key = "";
    int value;
    int beginidex = 0;
    int endidex = b.length;

    if (type != "file") {
      beginidex = 1;
      endidex = b.length - 1;
    }

    for (int i = beginidex; i < endidex; i++) {
      //b[i] = b[i].replaceAll("\n", "");
      if (b[i].replaceAll("\n", "").contains("("))
        if (null != getOneByRex(b[i], "[1-9]"))
          count++;
    }

    //if( count > max || count < min)    //modify, need to add check: durTime 
    if (count < (min - 1)) {
      System.out.println("=================================\nsize is different!");
      return false;
    }

    for (int i = beginidex; i < endidex; i++) {
      if (b[i].replaceAll("\n", "").contains("(")) {
        temp = getStringByRex(b[i], "\\(.*\\,.*\\)\\n");
        //放入hashmap中

        for (int j = 0; j < temp.length; j++) {
          key = temp[j].replaceAll("\n", "").replaceAll("\\(", "").replaceAll("\\)", "").split(",")[0];
          value =
            Integer.valueOf(temp[j].replaceAll("\n", "").replaceAll("\\(", "").replaceAll("\\)", "").split(",")[1]);

          if (result.containsKey(key))
            result.put(key, result.get(key) + value);
          else
            result.put(key, value);
        }
      }
    }

    for (int i = 0; i < expect.length; i++) {
      key = expect[i].split("=")[0];
      value = Integer.valueOf(expect[i].split("=")[1]);
      expt.put(key, value);
    }

    if (!result.equals(expt)) {
      System.out.println(result.get("is"));
      return false;
    }
    return true;

  }

  public static void replaceWordInSql() {
    String[] ipArr = {clusterTopo.client_ip, clusterTopo.node_ip[0], clusterTopo.node_ip[1], clusterTopo.node_ip[2]};
    int i = 0, len = ipArr.length;
    String replacedword1 = "\"\\/home\\/sparkTest\\/testData\\/sqldata\"";
    String replacedword2 = "\"\\/home\\/sqldata\"";
    String reWord =
      "\"\\/opt\\/DataSightBasePlatform_Automation\\/DataSightBasePlatform_TestCode_HQ\\/resources\\/spark\\/sqldata\"";
    for (i = 0; i < len; i++) {
      System.out.printf("=======Replacing " + ipArr[i] + "======");
      Sshcmd replace = new Sshcmd(ipArr[i], "root", "huawei");
      String cmd = "sh " + ConfigProvider.getValue("spark_test_file") + "/replace.sh " + SparkTestEnv.SQL_PATH + " "
        + replacedword1 + " " + reWord + ";";
      cmd += "sh " + ConfigProvider.getValue("spark_test_file") + "/replace.sh " + SparkTestEnv.SQL_PATH + " "
        + replacedword2 + " " + reWord + ";";
      cmd += "sh " + ConfigProvider.getValue("spark_test_file") + "/replace.sh " + SparkTestEnv.DATASIGHT_EXPECT + " "
        + replacedword1 + " " + reWord + ";";
      cmd += "sh " + ConfigProvider.getValue("spark_test_file") + "/replace.sh " + SparkTestEnv.DATASIGHT_EXPECT + " "
        + replacedword2 + " " + reWord + ";";
      replace.execAll(cmd);
    }
  }
  public static void replaceWordInSql_2_1() {
    String[] ipArr = {clusterTopo.client_ip, clusterTopo.node_ip[0], clusterTopo.node_ip[1], clusterTopo.node_ip[2]};
    int i = 0, len = ipArr.length;
    String replacedword1 = "\"\\/home\\/sparkTest\\/testData\\/sqldata\"";
    String replacedword2 = "\"\\/home\\/sqldata\"";
    String reWord =
            "\"\\/opt\\/DataSightBasePlatform_Automation\\/DataSightBasePlatform_TestCode_HQ\\/resources\\/spark2_1\\/sqldata\"";
    for (i = 0; i < len; i++) {
      System.out.printf("=======Replacing " + ipArr[i] + "======");
      Sshcmd replace = new Sshcmd(ipArr[i], "root", "huawei");
      String cmd = "sh " + ConfigProvider.getValue("spark2_1_test_file") + "/replace.sh " + SparkTestEnv.SQL_PATH_2_1 + " "
              + replacedword1 + " " + reWord + ";";
      cmd += "sh " + ConfigProvider.getValue("spark2_1_test_file") + "/replace.sh " + SparkTestEnv.SQL_PATH_2_1 + " "
              + replacedword2 + " " + reWord + ";";
      cmd += "sh " + ConfigProvider.getValue("spark2_1_test_file") + "/replace.sh " + SparkTestEnv.DATASIGHT_EXPECT_2_1+ " "
              + replacedword1 + " " + reWord + ";";
      cmd += "sh " + ConfigProvider.getValue("spark2_1_test_file") + "/replace.sh " + SparkTestEnv.DATASIGHT_EXPECT_2_1 + " "
              + replacedword2 + " " + reWord + ";";
      replace.execAll(cmd);
    }
  }

  public static void recoveryFileSys() {
    String[] ipArr = {clusterTopo.client_ip, clusterTopo.node_ip[0], clusterTopo.node_ip[1], clusterTopo.node_ip[2]};
    int i = 0, len = ipArr.length;
    for (i = 0; i < len; i++) {
      Sshcmd replace = new Sshcmd(ipArr[i], "root", "huawei");
      String cmd = "cp -r " + ConfigProvider.getValue("spark_test_file") + "/sqlcmd-exb/* " + SparkTestEnv.SQL_PATH;
      replace.execAll(cmd);
      cmd = "cp -r " + ConfigProvider.getValue("spark_test_file") + "/sqlexpect-exb/* " + SparkTestEnv.DATASIGHT_EXPECT;
      replace.execAll(cmd);
    }
  }
  public static void recoveryFileSys2_1() {
    String[] ipArr = {clusterTopo.client_ip, clusterTopo.node_ip[0], clusterTopo.node_ip[1], clusterTopo.node_ip[2]};
    int i = 0, len = ipArr.length;
    for (i = 0; i < len; i++) {
      Sshcmd replace = new Sshcmd(ipArr[i], "root", "huawei");
      String cmd = "cp -r " + ConfigProvider.getValue("spark2_1_test_file") + "/sqlcmd-exb/* " + SparkTestEnv.SQL_PATH_2_1;
      replace.execAll(cmd);
      cmd = "cp -r " + ConfigProvider.getValue("spark2_1_test_file") + "/sqlexpect-exb/* " + SparkTestEnv.DATASIGHT_EXPECT_2_1;
      replace.execAll(cmd);
    }
  }
  public static void cleanDB() {
    String[] ipArr = {clusterTopo.client_ip, clusterTopo.node_ip[0], clusterTopo.node_ip[1], clusterTopo.node_ip[2]};
    String cleanCmd = "rm -rf /home/securedn/metastore_db;rm -rf /root/metastore_db";
    int i = 0, len = ipArr.length;
    for (i = 0; i < len; i++) {
      Sshcmd clean = new Sshcmd(ipArr[i], "root", clusterTopo.root_pwd);
      clean.execAll(cleanCmd);
    }

  }

  public static ArrayList<String> getSparkSqlResult(String source, String logString) {
    ArrayList<String> ret = new ArrayList<String>();
    boolean flag = true;

    String[] lines = logString.split("\n");
    if ("file".equals(source)) {
      lines = RunSparkCmd.runLinuxCmd("cat " + logString)[2].split("\n");
    }

    for (int i = 0; i < lines.length; i++) {
      String line = lines[i].replaceAll("\\s+", " ").replaceFirst("^\\s+", "").replaceFirst("\\s+$", "");
      if (line.matches("^SET\\s+.*")) {
        if (flag && line.matches("^SET\\s+spark.sql.hive.version=.*")) {
          ret.add(line.substring(line.indexOf("spark"), line.length()));
          flag = false;
        } else {
          continue;
        }
      } else if ((line.matches("^\\s+$")) || flag) {
        continue;
      } else {
        ret.add(line);
      }

    }
    return ret;
  }

  public static ArrayList<String> getSparkSqlExpect(String source, String logString) {
    ArrayList<String> ret = new ArrayList<String>();

    String[] lines = logString.split("\n");
    if ("file".equals(source)) {
      lines = RunSparkCmd.runLinuxCmd("cat " + logString)[2].split("\n");
    }

    for (int i = 0; i < lines.length; i++) {
      if (lines[i].startsWith("+")) {
        if ((i < (lines.length - 1)) && (lines[i + 1].startsWith("|"))) {
          i += 2;
        }
        continue;
      } else if (lines[i].startsWith("|")) {
        String line = lines[i].replaceAll("\\s+", " ");
        line = line.replaceAll("\\|\\|", "@@");
        line = line.replaceAll("\\|\\s*", "").replaceFirst("\\s+$", "");
        line = line.replaceAll("@@", "||");
        if (line.matches("^\\s+$")) {
          continue;
        }

        ret.add(line);
      } else {
        if (lines[i].matches("^\\s*$")) {
          continue;
        }
        ret.add("Running SQL: " + lines[i]);
      }
    }
    return ret;
  }
