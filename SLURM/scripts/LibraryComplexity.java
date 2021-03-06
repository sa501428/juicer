/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2011-2015 Broad Institute, Aiden Lab
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 *
 * Based on PicardTools 
 * Juicer version 1.5
 */

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Locale;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

class LibraryComplexity {
  public static void main(String[] args) {
    if (args.length != 2 && args.length != 3 && args.length != 1) {
      System.out.println("Usage: java LibraryComplexity <directory> <output file>");
      System.out.println("     : java LibraryComplexity <unique> <pcr> <opt>");
      System.exit(0);
    }
    NumberFormat nf = NumberFormat.getInstance(Locale.US);

      AtomicBoolean somethingFailed = new AtomicBoolean(false);
    long readPairs = 0;
    long uniqueReadPairs = 0;
    long opticalDups = 0;
    long totalReadPairs = 0;
    if (args.length == 2 || args.length==1) {
      try {
          ExecutorService executor = Executors.newFixedThreadPool(3);

          Callable<Long> taskOptDups = () -> {
              File f = new File(args[0] + "/opt_dups.txt");
              if (f.exists()) {
                  try {
                      long opticalDupsT = 0L;
                      BufferedReader reader = new BufferedReader(new FileReader(f));
                      while (reader.readLine() != null) opticalDupsT++;
                      reader.close();
                      return opticalDupsT;
                  } catch (Exception e) {
                      somethingFailed.set(true);
                      return 0L;
                  }
              } else {
                  return 0L;
              }
          };

          Callable<Long> taskUniqueReads = () -> {
              File f = new File(args[0] + "/merged_nodups.txt");
              if (f.exists()) {
                  try {
                      long uniqueReadPairsT = 0L;
                      BufferedReader reader = new BufferedReader(new FileReader(f));
                      while (reader.readLine() != null) uniqueReadPairsT++;
                      reader.close();
                      return uniqueReadPairsT;
                  } catch (Exception e) {
                      somethingFailed.set(true);
                      return 0L;
                  }
              } else {
                  return 0L;
              }
          };

          Callable<Long> taskReadPairs = () -> {
              File f = new File(args[0] + "/dups.txt");
              if (f.exists()) {
                  try {
                      long readPairsT = 0;
                      BufferedReader reader = new BufferedReader(new FileReader(args[0] + "/dups.txt"));
                      while (reader.readLine() != null) readPairsT++;
                      reader.close();
                      return readPairsT;
                  } catch (Exception e) {
                      somethingFailed.set(true);
                      return 0L;
                  }
              } else {
                  return 0L;
              }
          };

          Future<Long> futureOptDups = executor.submit(taskOptDups);
          Future<Long> futureUniqueReads = executor.submit(taskUniqueReads);
          Future<Long> futureReadPairs = executor.submit(taskReadPairs);

        String fname = "inter.txt";
        if (args.length == 2) fname = args[1];
          File f = new File(args[0] + "/" + fname);
        if (f.exists()) {
            BufferedReader reader = new BufferedReader(new FileReader(args[0] + "/" + fname));
          String line = reader.readLine();
          boolean done = false;
          while (line != null && !done) {
            if (line.contains("Sequenced Read")) {
              String[] parts = line.split(":");
              try {
                totalReadPairs = nf.parse(parts[1].trim()).longValue();
              } catch (ParseException ignored) {
              }
              done = true;
            }
            line = reader.readLine(); 
          }
          reader.close();
        }

          opticalDups = futureOptDups.get();
          uniqueReadPairs = futureUniqueReads.get();
          readPairs = futureReadPairs.get();
          executor.shutdown();

          if (somethingFailed.get()) {
              System.err.println("Something failed in a thread");
              System.exit(1);
          }

      } catch (IOException error) {
          System.err.println("Problem counting lines in merged_nodups and dups");
          System.exit(1);
      } catch (InterruptedException e) {
          System.err.println("Threads interrupted exception");
          System.exit(1);
      } catch (ExecutionException e) {
          System.err.println("Threads execution exception");
          System.exit(1);
      }
    }
    else {
      try {
        uniqueReadPairs = Integer.valueOf(args[0]);
        readPairs = Integer.valueOf(args[1]);
        opticalDups = Integer.valueOf(args[2]);
      }
      catch (NumberFormatException error) {
        System.err.println("When called with three arguments, must be integers");
        System.exit(1);
      }

    }

      readPairs += uniqueReadPairs;
    NumberFormat decimalFormat = NumberFormat.getPercentInstance();
    decimalFormat.setMinimumFractionDigits(2);
    decimalFormat.setMaximumFractionDigits(2);

    System.out.print("Unique Reads: " + NumberFormat.getInstance().format(uniqueReadPairs) + " ");
    if (totalReadPairs > 0) {
      System.out.println("(" + decimalFormat.format(uniqueReadPairs/(double)totalReadPairs) + ")");
    }
    else {
      System.out.println();
    }

    System.out.print("PCR Duplicates: " + nf.format(readPairs - uniqueReadPairs) + " ");
    if (totalReadPairs > 0) {
      System.out.println("(" + decimalFormat.format((readPairs - uniqueReadPairs)/(double)totalReadPairs) + ")");
    }
    else {
      System.out.println();
    }
    System.out.print("Optical Duplicates: " + nf.format(opticalDups) + " ");
    if (totalReadPairs > 0) {
      System.out.println("(" + decimalFormat.format(opticalDups/(double)totalReadPairs) + ")");
    }
    else {
      System.out.println();
    }
    long result;
    try {
      result = estimateLibrarySize(readPairs, uniqueReadPairs);
    }
    catch (NullPointerException e) {
      result = 0;
    }
    System.out.println("Library Complexity Estimate: " + nf.format(result));
  }

  /**
   * Estimates the size of a library based on the number of paired end molecules 
   * observed and the number of unique pairs observed.
   * <br>
   * Based on the Lander-Waterman equation that states:<br>
   *     C/X = 1 - exp( -N/X )<br>
   * where<br>
   *     X = number of distinct molecules in library<br>
   *     N = number of read pairs<br>
   *     C = number of distinct fragments observed in read pairs<br>
   */
  private static long estimateLibrarySize(final long readPairs,
                                          final long uniqueReadPairs) {
    final long readPairDuplicates = readPairs - uniqueReadPairs;
    
    if (readPairs > 0 && readPairDuplicates > 0) {

        double m = 1.0, M = 100.0;

        if (uniqueReadPairs >= readPairs || f(m * uniqueReadPairs, uniqueReadPairs, readPairs) < 0) {
            throw new IllegalStateException("Invalid values for pairs and unique pairs: " + readPairs + ", " + uniqueReadPairs);
	    }

        while (f(M * uniqueReadPairs, uniqueReadPairs, readPairs) >= 0) {
        m = M;
        M *= 10.0;
	    }
      
	    double r = (m+M)/2.0;
        double u = f(r * uniqueReadPairs, uniqueReadPairs, readPairs);
	    int i = 0;
	    while (u != 0 && i < 1000) {
        if (u > 0) m = r;
        else M = r;
        r = (m+M)/2.0;
            u = f(r * uniqueReadPairs, uniqueReadPairs, readPairs);
        i++;
	    }
	    if (i == 1000) {
        System.err.println("Iterated 1000 times, returning estimate");
	    }

        return (long) (uniqueReadPairs * (m + M) / 2.0);
    }
    else {
        return 0;
    }
  }
  
  /** Method that is used in the computation of estimated library size. */
  private static double f(double x, double c, double n) {
    return c/x - 1 + Math.exp(-n/x);
  }
}    
