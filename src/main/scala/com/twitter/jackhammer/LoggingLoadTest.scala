package com.twitter.zookeeperloadtest

import java.io.{BufferedReader, File, FileReader, FileWriter}
import java.util.concurrent.{ConcurrentLinkedQueue, Executors, TimeUnit}
import scala.collection.mutable
import net.lag.configgy.{Config, Configgy}
import net.lag.logging.Logger
import com.twitter.ostrich.Stats


trait LoggingLoadTest {
  class ParallelWorkUnit[A, B](runs: Int, work: A => B) extends Runnable {
    def run {
      for (i <- 0 to runs) {
        runWithTiming(work)
      }
    }
  }

  Configgy.configure("src/main/resources/config.conf")

  private val log = Logger.get
  private val config = Configgy.config

  val logOutput = new ConcurrentLinkedQueue[String]()

  def runWithTimingNTimes[A, B](runs: Int)(f: A => B) {
    for (i <- 1 to runs) runWithTiming(f)
  }

  def runInParallelNTimes[A, B](workers: Int, runs: Int)(f: A => B) {
    val pool = Executors.newFixedThreadPool(workers)

    for (i <- 1 to workers) {
      val workUnit = new ParallelWorkUnit(runs, f)
      pool.submit(workUnit)
      log.info("submitted work unit for parallel execution")
    }

    log.info("awaiting termination")
    pool.awaitTermination(45, TimeUnit.SECONDS)
  }

  def runWithTiming[A, B](f: A => B) {
    val time = System.currentTimeMillis
    var error = 0

    val (_, duration) = Stats.duration[Unit] {
      try {
        f
      } catch {
        case e: Exception => {
          log.error("unable to execute timed method: %s", e.getMessage())
          error = 1
        }
      }
    }

    // output columns:
    // 1. operation start time
    // 2. if there have EVER been errors during the run
    // 3. how long the operation took
    //
    // all timings are in milliseconds
    logOutput.offer("%s %d %d".format(time, error, duration))
  }

  def getLinesFromLog(requestLogFilename: String): List[String] = {
    val requestLogFile = new File(requestLogFilename)

    if (!requestLogFile.exists) {
      throw new Exception("log file not found: %s".format(requestLogFilename))
    }

    if (!requestLogFile.canRead) {
      throw new Exception("log file cannot be read: %s".format(requestLogFilename))
    }

    val logReader = new FileReader(requestLogFile)
    val bufferedLogReader = new BufferedReader(logReader)
    val logLines = new mutable.ListBuffer[String]()

    while (bufferedLogReader.ready) {
      logLines += bufferedLogReader.readLine
    }

    logLines.toList
  }

  def dumpLogOutput {
    val logOutputIter = logOutput.iterator
    val outputFilePath = "%s-%d.log".format(config.getString("test-runs-path").get, System.currentTimeMillis)

    val outputFile = new File(outputFilePath)
    val writer = new FileWriter(outputFile)

    while (logOutputIter.hasNext) {
      val logLine = logOutputIter.next
      writer.write(logLine + "\n")
    }

    writer.flush
    writer.close

    log.info("test run statistics dumped to %s", outputFilePath)
  }
}
