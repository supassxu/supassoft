
  public static void ResultLimitExtract(String result, ArrayList<HashSet<String>> ListRet) {
    //将结果按行分开，逐句寻找命令，表头，结果内容
	//According to the line breaks, the result by the sentence look for command, the header, result
    result = result.replaceAll(";\\s\n", ";\n"); //2015-10-9 修改
    result = result.replaceAll("\\s\n", ""); //2015-10-9 修改
    String[] ResultSplit = result.split("\n");
    //        String [] Resultnullspace = null;

    int ReslutSplit_length = ResultSplit.length;
    int j = 0, count = 0;

    for (; j < ReslutSplit_length; j++) {
      HashSet<String> Headtemp = new HashSet<String>();
      if (!ResultSplit[j].contains("+-")) {
        Headtemp.add(ResultSplit[j].replaceAll(" ", "").replaceAll("\t", ""));
        ListRet.add(count, Headtemp);
        count++;
      }

    }
  }
  /*
  create by mWX314046
  get histogram
   */
  public static String checkhistogramResult_CBO( String result) {
    String matchresult = null;
    int localindex ,index;

    result = result.replaceAll(";\\s\n", ";\n");
    result = result.replaceAll("\\s\n", "");
    String[] ResultSplit = result.split("\n");
    int ReslutSplit_length = ResultSplit.length;
    int j = 0;

    for (; j < ReslutSplit_length; j++) {
      ResultSplit[j]=ResultSplit[j].replaceAll("\\s", "");
      if (ResultSplit[j].contains("a#Histogram0")) {
        localindex = ResultSplit[j].indexOf("a#Histogram0");
        index = localindex + "a#Histogram0".length();
        matchresult = ResultSplit[j].substring(index + 1,ResultSplit[j].length()-1);
        break;
      }
    }
    return matchresult;
  }

  public static void cleanHbase(String Command) {
    String client_path = clusterTopo.client_path;
    String source_client = "source " + client_path + "bigdata_env" + ";";
    execCommand.exec(source_client + "echo \"list\"|hbase shell");
    if ("htb_t1" == Command) {
      execCommand.exec(source_client + "echo \"disable 'htb_t1'\"|hbase shell");
      execCommand.exec(source_client + "echo \"drop 'htb_t1'\"|hbase shell");
    } else if ("htb_t2" == Command) {
      execCommand.exec(source_client + "echo \"disable 'htb_t2'\"|hbase shell");
      execCommand.exec(source_client + "echo \"drop 'htb_t2'\"|hbase shell");
    }
    execCommand.exec(source_client + "echo \"list\"|hbase shell");
  }

  public static void cleanZK(String Command) {
    String client_path = clusterTopo.client_path;
    String source_client = "source " + client_path + "bigdata_env" + ";";
    execCommand.exec(source_client + "echo \"ls /luna/collections\"|sh zkCli.sh -server " + Command + ":24002");
    execCommand.exec(source_client + "echo \"delete /luna/collections/sc1/index_mapping\"|sh zkCli.sh -server " + Command + ":24002");
    execCommand.exec(source_client + "echo \"delete /luna/collections/sc1\"|sh zkCli.sh -server " + Command + ":24002");
    execCommand.exec(source_client + "echo \"ls /luna/collections\"|sh zkCli.sh -server " + Command + ":24002");


    execCommand.exec(source_client + "echo \"ls /luna/collections\"|sh zkCli.sh -server " + Command + ":24002");
    execCommand.exec(source_client + "echo \"delete /luna/collections/sc6/index_mapping\"|sh zkCli.sh -server " + Command + ":24002");
    execCommand.exec(source_client + "echo \"delete /luna/collections/sc6\"|sh zkCli.sh -server " + Command + ":24002");
    execCommand.exec(source_client + "echo \"ls /luna/collections\"|sh zkCli.sh -server " + Command + ":24002");

  }

  public static void cleanSolr() {
    String client_path = clusterTopo.client_path;
    String source_client = "source " + client_path + "bigdata_env" + ";";
    execCommand.exec(source_client + "solrctl collection --list");
    execCommand.exec(source_client + "solrctl collection --delete sc1");
    execCommand.exec(source_client + "solrctl collection --list");
  }

}
