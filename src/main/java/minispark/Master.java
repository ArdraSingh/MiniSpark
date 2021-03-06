package minispark;

import tutorial.*;

/**
 * Created by lzb on 4/16/17.
 */

import org.apache.thrift.TException;
import org.apache.thrift.transport.TSSLTransportFactory;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TSSLTransportFactory.TSSLTransportParameters;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import tutorial.WorkerService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

public class Master {
  HashMap<String, WorkerService.Client[]> clients;
  HashMap<WorkerService.Client, Boolean> availableMap;
  public static final int numClientsPerWorker = 4;
  public static final int sleepTime = 5000;
  public static final Object lock = new Object();

  public static String[] workerDNSs = {
      "ip-172-31-38-195.ec2.internal",
      "ip-172-31-40-15.ec2.internal"
  };


  public Master() {
    try {
      clients = new HashMap<>();
      availableMap = new HashMap<>();

      for (String workerDNS: workerDNSs) {
        clients.put(workerDNS, new WorkerService.Client[numClientsPerWorker]);
        for (int i = 0; i < numClientsPerWorker; ++i) {
          TTransport transport = new TSocket(workerDNS, 9090);
          transport.open();
          TProtocol protocol = new  TBinaryProtocol(transport);
          clients.get(workerDNS)[i] = new WorkerService.Client(protocol);
          availableMap.put(clients.get(workerDNS)[i], true);
        }
      }
    } catch (TException x) {
      x.printStackTrace();
    }
  }

  public String findLeastLoaded(List<String> arrayList) {
    int maxNum = -1;
    String result = "";
    for (String hostName: arrayList) {
      int freeNum = 0;
      for (int i = 0; i < numClientsPerWorker; ++i) {
        if ((availableMap.get(clients.get(hostName)[i]))) {
          ++freeNum;
        }
      }
      if (freeNum > maxNum) {
        maxNum = freeNum;
        result = hostName;
      }
    }
    if (maxNum == -1) {
      for (String hostName: workerDNSs) {
        int freeNum = 0;
        for (int i = 0; i < numClientsPerWorker; ++i) {
          if ((availableMap.get(clients.get(hostName)[i]))) {
            ++freeNum;
          }
        }
        if (freeNum > maxNum) {
          maxNum = freeNum;
          result = hostName;
        }
      }
      if (maxNum == -1) {
        return arrayList.get(new Random().nextInt() % arrayList.size());
      } else {
        return result;
      }
    } else {
      return result;
    }
  }

  public DoJobReply assignJob(String hostName, ArrayList<DoJobArgs> args) throws TException {
    while (true) {
      int index = -1;
      synchronized (lock) {
        for (int i = 0; i < numClientsPerWorker; ++i) {
          if (availableMap.get(clients.get(hostName)[i])) {
            availableMap.put(clients.get(hostName)[i], false);
            index = i;
            break;
          }
        }
      }

      if (index != -1) {
        DoJobReply reply = null;
        synchronized (clients.get(hostName)[index]) {
          reply = clients.get(hostName)[index].doJob(args);
        }
        synchronized (lock) {
          availableMap.put(clients.get(hostName)[index], true);
        }
        return reply;
      } else {
        try {
          Thread.sleep(sleepTime);
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    }
  }
}
