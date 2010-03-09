package com.twitter.jackhammer

import org.specs._


class LoggingLoadTestSpec extends Specification with LoggingLoadTest {
  "LoggingLoadTest" should {
    var counter = 0

    doBefore {
      counter = 0
      counter mustEqual 0
    }

    "increment a counter once when running with timing" in {
      val result: Int = runWithTiming {
        counter += 1
        counter
      }

      counter mustEqual 1
      result mustEqual 1
    }

    "increment a counter for every one of ten runs of runWithTimingNTimes" in {
      runWithTimingNTimes(10) {
        counter += 1
      }

      counter mustEqual 10
    }

    "increment a counter for one of ten parallel runs of runInParallelNTimes" in {
      runInParallelNTimes(10) {
        counter += 1
        Thread.sleep(20) // simulate the delay of performing a non-trival computation
      }

      counter must be(10).eventually
    }

    "log runs and dump log output to a file" in {
      runWithTimingNTimes(10) {
        counter += 1
      }

      logOutput.isEmpty mustBe false

      val logfile = dumpLogOutput
      logfile.exists mustBe true
    }
  }
}
