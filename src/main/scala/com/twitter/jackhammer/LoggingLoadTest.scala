package com.twitter.jackhammer

import java.io.{BufferedReader, File, FileReader, FileWriter}
import java.util.concurrent.{ConcurrentLinkedQueue, CountDownLatch}
import scala.actors.Actor
import scala.actors.Actor._
import scala.collection.{immutable, mutable}
import com.twitter.ostrich.Stats
import net.lag.configgy.{Config, Configgy}
import net.lag.logging.Logger


trait LoggingLoadTest {
  Configgy.configure("src/main/resources/config.conf")

  private val log = Logger.get
  private val config = Configgy.config

  val logOutput = new ConcurrentLinkedQueue[String]()

  def runWithTimingNTimes[T](runs: Int)(f: => T) {
    for (i <- 1 to runs) {
      runWithTiming(f)
    }
  }

  def runInActorNTimes[T](runs: Int)(f: => T) {
    val runner = actor {
      loop {
        react {
          case f: Function[_, _] => f
        }
      }
    }

    for (i <- 1 to runs) {
      runner ! runWithTiming(f)
    }
  }

  def runInNParallelThreadsMTimes[T](threads: Int, runs: Int)(f: => T) {
    val countDownLatch = new CountDownLatch(runs * threads)
    var threadList = new mutable.ListBuffer[Thread]()

    for (i <- 1 to threads) {
      threadList += new Thread {
	      runWithTimingNTimes(runs) {
	        f
	        countDownLatch.countDown()
	      }
      }
    }

    threadList.map { thread => thread.run }
    countDownLatch.await()
  }

  def runWithTiming[T](f: => T): T = {
    val time = System.currentTimeMillis
    val (result, duration) = Stats.duration[T] { f }

    // output columns:
    // 1. operation start time
    // 2. how long the operation took
    logOutput.offer("%s %d".format(time, duration))

    result
  }

  def dumpLogOutput: File = {
    val tmpFile = File.createTempFile("loadtest-%d".format(System.currentTimeMillis), "log")
    dumpLogOutput(tmpFile)
    tmpFile
  }

  def dumpLogOutput(outputFile: File) {
    val logOutputIter = logOutput.iterator
    val writer = new FileWriter(outputFile)

    while (logOutputIter.hasNext) {
      val logLine = logOutputIter.next
      writer.write(logLine + "\n")
    }

    writer.flush
    writer.close

    log.info("test run statistics dumped to %s", outputFile.getPath)
  }
}
