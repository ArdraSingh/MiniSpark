package minispark;

import org.apache.thrift.TException;
import tutorial.StringIntPair;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by lzb on 4/16/17.
 */
public class App {

  public static boolean filterTest(String s) {
    return (s.hashCode() % 2 == 1);
  }

  public static String mapTest(String s) {
    return s.toLowerCase();
  }

  public static ArrayList<String> flatMapTest(String s) {
    return new ArrayList<String>(Arrays.asList(s.split(" ")));
  }

  public static int reduceByKeyTest(int a, int b) {
    return a + b;
  }

  public static StringIntPair mapCount(String s) {
    return new StringIntPair(s, 1);
  }

  public static void main(String[] args) throws IOException, TException {
    SparkContext sc = new SparkContext("Example");
    Rdd lines = sc.textFile("webhdfs://ec2-34-201-24-238.compute-1.amazonaws.com/test.txt");
    Rdd pairs = lines.flatMap("flatMapTest").map("mapTest").mapPair("mapCount").reduceByKey("reduceByKeyTest");

    System.out.println("Count result: " + pairs.count());

    Rdd filteredPair = pairs.filter("filterTest");

    System.out.println("FIltered Count result: " + filteredPair.count());

    List<StringIntPair> output = (List<StringIntPair>) filteredPair.collect();

    for (StringIntPair pair: output) {
      System.out.println(pair.toString());
    }

    System.out.println(pairs.reduce("reduceByKeyTest"));
  }
}
