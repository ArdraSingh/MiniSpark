package minispark;

/**
 * Created by lzb on 5/4/17.
 */

import org.apache.thrift.TException;
import tutorial.*;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class WorkerServiceHandler implements WorkerService.Iface {
  public static HashMap<Integer, Object> hashMap = new HashMap<Integer, Object>();

  public static int reduceHelper(Method method, ArrayList<Integer> values) {
    assert !values.isEmpty();
    if (values.size() == 1) {
      return values.get(0);
    } else {
      int initVal = values.get(0);
      for (int i = 1; i < values.size(); ++i) {
        try {
          initVal = (int) method.invoke(null, initVal, values.get(i));
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
      return initVal;
    }
  }

  public ArrayList<StringIntPair> readPartition(int partitionId) {
    System.out.println("Received readPartition");
    assert hashMap.containsKey(partitionId);
    return (ArrayList<StringIntPair>) hashMap.get(partitionId);
  }

  public DoJobReply doJob(List<DoJobArgs> argsArr) throws TException {
    DoJobReply reply = new DoJobReply();
    DoJobArgs args = argsArr.get(argsArr.size() - 1);
    Long start = System.currentTimeMillis();
    if (argsArr.size() == 1) {
      switch (args.workerOpType) {
        case HashSplit:
          System.out.println("Received HashSplit");
          assert argsArr.size() == 1;
          // Check if already in memory first
          boolean flag = true;
          int size = args.shufflePartitionIds.size();
          for (int shufflePartitionId : args.shufflePartitionIds) {
            if (!hashMap.containsKey(shufflePartitionId)) {
              flag = false;
            }
          }
          if (flag) {
            // Already in memory
            break;
          }
          ArrayList<StringIntPair>[] hashedResults = new ArrayList[size];
          for (int i = 0; i < size; ++i) {
            hashedResults[i] = new ArrayList<>();
          }
          for (StringIntPair pair : (ArrayList<StringIntPair>) hashMap.get(args.inputId)) {
            hashedResults[Math.abs(pair.str.hashCode()) % size].add(pair);
          }
          for (int i = 0; i < size; ++i) {
            hashMap.put(args.shufflePartitionIds.get(i), hashedResults[i]);
          }
          hashMap.remove(args.inputId);
          return reply;
        case ReduceJob:
          assert argsArr.size() == 1;
          if (!hashMap.containsKey(args.partitionId)) {
            // Output doesn't exist?
            System.out.println("Reduce on non-materialized data");
          } else {
            ArrayList<StringIntPair> tmp = (ArrayList<StringIntPair>) hashMap.get(args.partitionId);
            reply.reduceResult = tmp.get(0).num;
            for (int i = 1; i < tmp.size(); ++i) {
              try {
                Method method = App.class.getMethod(args.funcName, int.class, int.class);
                method.invoke(null, reply.reduceResult, tmp.get(i).num);
              } catch (Exception e) {
                e.printStackTrace();
              }
            }
          }
          return reply;
        case CountPairJob:
          assert argsArr.size() == 1;
          if (!hashMap.containsKey(args.partitionId)) {
            // Output doesn't exist?
            System.out.println("CountPair on non-materialized data");
          } else {
            reply.reduceResult = ((ArrayList<StringIntPair>) hashMap.get(args.partitionId)).size();
          }
          return reply;
        case CountJob:
          assert argsArr.size() == 1;
          if (!hashMap.containsKey(args.partitionId)) {
            // Output doesn't exist?
            System.out.println("Count on non-materialized data");
          } else {
            reply.reduceResult = ((ArrayList<String>) hashMap.get(args.partitionId)).size();
          }
          return reply;
        case ReduceByKeyJob:
          System.out.println("Received ReduceByKeyJob");
          assert argsArr.size() == 1;
          if (hashMap.containsKey(args.partitionId)) {
            // Output already exists
          } else {
            ArrayList<StringIntPair> lines = Worker.readPartitions(args.inputIds, args.inputHostNames);
            HashMap<String, ArrayList<Integer>> kvStore = new HashMap<>();
            for (StringIntPair pair : lines) {
              if (kvStore.containsKey(pair.str)) {
                kvStore.get(pair.str).add(pair.num);
              } else {
                ArrayList<Integer> arrayList = new ArrayList<>();
                arrayList.add(pair.num);
                kvStore.put(pair.str, arrayList);
              }
            }
            ArrayList<StringIntPair> output = new ArrayList<>();
            try {
              Method method = App.class.getMethod(args.funcName, int.class, int.class);
              for (Map.Entry<String, ArrayList<Integer>> entry : kvStore.entrySet()) {
                output.add(new StringIntPair(entry.getKey(), reduceHelper(method, entry.getValue())));
              }
            } catch (Exception e) {
              e.printStackTrace();
            }
            hashMap.put(args.partitionId, output);
          }
          return reply;
        case GetSplit:
          System.out.println("Received GetSplit");
          if (hashMap.containsKey(args.partitionId)) {
            reply.lines = (ArrayList<String>) hashMap.get(args.partitionId);
          } else {
            System.out.println("GetSplit Exception");
          }
          return reply;
        case GetPairSplit:
          System.out.println("Received GetPairSplit");
          if (hashMap.containsKey(args.partitionId)) {
            reply.pairs = (ArrayList<StringIntPair>) hashMap.get(args.partitionId);
          } else {
            System.out.println("GetPairSplit Exception");
          }
          return reply;
        case MapJob:
          System.out.println("Received MapJob");
          if (hashMap.containsKey(args.partitionId)) {
            // reply.lines = (ArrayList<String>) hashMap.get(args.partitionId);
          } else {
            try {
              Method method = App.class.getMethod(args.funcName, String.class);
              ArrayList<String> input = (ArrayList<String>) hashMap.get(args.inputId);
              ArrayList<String> output = new ArrayList<>();
              for (String str : input) {
                output.add((String) method.invoke(null, str));
              }
              hashMap.put(args.partitionId, output);
              hashMap.remove(args.inputId);
            } catch (Exception e) {
              e.printStackTrace();
            }
          }
          return reply;
        case MapPairJob:
          System.out.println("Received MapPairJob");
          if (hashMap.containsKey(args.partitionId)) {
            // reply.pairs = (ArrayList<StringIntPair>) hashMap.get(args.partitionId);
          } else {
            try {
              Method method = App.class.getMethod(args.funcName, String.class);
              ArrayList<String> input = (ArrayList<String>) hashMap.get(args.inputId);
              ArrayList<StringIntPair> output = new ArrayList<>();
              for (String str : input) {
                output.add((StringIntPair) method.invoke(null, str));
              }
              hashMap.put(args.partitionId, output);
              hashMap.remove(args.inputId);
            } catch (Exception e) {
              e.printStackTrace();
            }
          }
          return reply;
        case FilterJob:
          System.out.println("Received FilterJob");
          if (hashMap.containsKey(args.partitionId)) {
            // already exists
          } else {
            try {
              Method method = App.class.getMethod(args.funcName, String.class);
              ArrayList<String> input = (ArrayList<String>) hashMap.get(args.inputId);
              ArrayList<String> output = new ArrayList<>();
              for (String str : input) {
                if ((boolean) method.invoke(null, str)) {
                  output.add(str);
                }
              }
              hashMap.put(args.partitionId, output);
              hashMap.remove(args.inputId);
            } catch (Exception e) {
              e.printStackTrace();
            }
          }
          return reply;
        case FilterPairJob:
          System.out.println("Received FilterPairJob");
          if (hashMap.containsKey(args.partitionId)) {
            // already exists
          } else {
            try {
              Method method = App.class.getMethod(args.funcName, String.class);
              ArrayList<StringIntPair> input = (ArrayList<StringIntPair>) hashMap.get(args.inputId);
              ArrayList<StringIntPair> output = new ArrayList<>();
              for (StringIntPair str : input) {
                if ((boolean) method.invoke(null, str)) {
                  output.add(str);
                }
              }
              hashMap.put(args.partitionId, output);
              hashMap.remove(args.inputId);
            } catch (Exception e) {
              e.printStackTrace();
            }
          }
          return reply;
        case FlatMapJob:
          System.out.println("Received FlatMapJob");
          if (hashMap.containsKey(args.partitionId)) {
            // reply.lines = (ArrayList<String>) hashMap.get(args.partitionId);
          } else {
            try {
              Method method = App.class.getMethod(args.funcName, String.class);
              ArrayList<String> input = (ArrayList<String>) hashMap.get(args.inputId);
              ArrayList<String> output = new ArrayList<>();
              for (String str : input) {
                output.addAll((List<String>) method.invoke(null, str));
              }
              hashMap.put(args.partitionId, output);
              hashMap.remove(args.inputId);
            } catch (Exception e) {
              e.printStackTrace();
            }
          }
          return reply;
        case DelSplit:
          System.out.println("Received DelSplit");
          hashMap.clear();
          return reply;
        default:
          assert false;
      }
    }

    // TODO: assume no caching here, always start from ParaJob or ReadHdfsSplit
    // TODO: Should detect the latest partition that is in memory and start from there

    List<String> starter = null;
    args = argsArr.get(0);
    if (args.workerOpType == WorkerOpType.ParaJob) {
      System.out.println("Received ParaJob");
      assert !args.inputHostNames.isEmpty();
      starter = args.inputHostNames;
    } else if (args.workerOpType == WorkerOpType.ReadHdfsSplit) {
      try {
        starter = HdfsSplitReader.HdfsSplitRead(args.filePath, args.hdfsSplitId);
      } catch (Exception e) {
        e.printStackTrace();
      }
    } else {
      assert false;
    }

    Long end = System.currentTimeMillis();
    System.out.println("Get init input used time: " + (end - start) / 1000);
    Long start1 = System.currentTimeMillis();

    ArrayList<String> strResult = new ArrayList<>();
    ArrayList<StringIntPair> pairResult = new ArrayList<>();
    ArrayList<String> flatStrs = null;

    Method[] methods = new Method[argsArr.size()];

    for (int i = 1; i < argsArr.size(); ++i) {
      args = argsArr.get(i);
      if (args.workerOpType == WorkerOpType.FilterPairJob) {
        try {
          methods[i] = App.class.getMethod(args.funcName, StringIntPair.class);
        } catch (Exception e) {
          e.printStackTrace();
        }
      } else {
        try {
          methods[i] = App.class.getMethod(args.funcName, String.class);
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    }

    for (String strTmp : starter) {
      StringIntPair pairTmp = null;
      int i = 1;
      boolean flatMapped = false;
      boolean preserve = true;
      for (; i < argsArr.size(); ++i) {
        Method method = methods[i];
        switch (args.workerOpType) {
          case MapJob:
            try {
              strTmp = (String) method.invoke(null, strTmp);
            } catch (Exception e) {
              e.printStackTrace();
            }
            break;
          case MapPairJob:
            try {
              pairTmp = (StringIntPair) method.invoke(null, strTmp);
            } catch (Exception e) {
              e.printStackTrace();
            }
            break;
          case FilterJob:
            try {
              preserve = (boolean) method.invoke(null, strTmp);
              if (!preserve) {
                // break directly
                i = argsArr.size();
                break;
              }
            } catch (Exception e) {
              e.printStackTrace();
            }
            break;
          case FlatMapJob:
            flatMapped = true;
            try {
              flatStrs = (ArrayList<String>) method.invoke(null, strTmp);
            } catch (Exception e) {
              e.printStackTrace();
            }
            break;
          case FilterPairJob:
            try {
              preserve = (boolean) method.invoke(null, pairTmp);
              if (!preserve) {
                // break directly
                i = argsArr.size();
                break;
              }
            } catch (Exception e) {
              e.printStackTrace();
            }
            break;
          default:
            assert false;
        }
        if (flatMapped) {
          ++i;
          break;
        }
      }

      if (i == argsArr.size()) {
        if (preserve) {
          if (pairTmp != null) {
            pairResult.add(pairTmp);
          } else {
            strResult.add(strTmp);
          }
        }
      } else {
        for (String flatStr : flatStrs) {
          strTmp = flatStr;
          pairTmp = null;
          preserve = true;
          int index = i;
          for (; index < argsArr.size(); ++index) {
            Method method = methods[index];
            switch (args.workerOpType) {
              case MapJob:
                try {
                  strTmp = (String) method.invoke(null, strTmp);
                } catch (Exception e) {
                  e.printStackTrace();
                }
                break;
              case MapPairJob:
                try {
                  pairTmp = (StringIntPair) method.invoke(null, strTmp);
                } catch (Exception e) {
                  e.printStackTrace();
                }
                break;
              case FilterJob:
                try {
                  preserve = (boolean) method.invoke(null, strTmp);
                  if (!preserve) {
                    // break directly
                    index = argsArr.size();
                    break;
                  }
                } catch (Exception e) {
                  e.printStackTrace();
                }
                break;
              case FilterPairJob:
                try {
                  preserve = (boolean) method.invoke(null, pairTmp);
                  if (!preserve) {
                    // break directly
                    index = argsArr.size();
                    break;
                  }
                } catch (Exception e) {
                  e.printStackTrace();
                }
                break;
              default:
                assert false;
            }
          }

          if (index == argsArr.size() && preserve) {
            if (pairTmp != null) {
              pairResult.add(pairTmp);
            } else {
              strResult.add(strTmp);
            }
          }
        }
      }
    }
    if (!strResult.isEmpty()) {
      hashMap.put(argsArr.get(argsArr.size() - 1).partitionId, strResult);
    } else {
      hashMap.put(argsArr.get(argsArr.size() - 1).partitionId, pairResult);
    }

    Long end1 = System.currentTimeMillis();
    System.out.println("Transformtion Used time: " + (end1 - start1) / 1000);

    return reply;
  }
}
