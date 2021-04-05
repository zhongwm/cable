/*
 * Copyright 2020-2021, Wenming Zhong
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

/* Written by Wenming Zhong */

package zhongwm.cable.util
import java.io.ByteArrayInputStream

import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.joran.JoranConfigurator
import ch.qos.logback.core.joran.spi.JoranException
import org.slf4j.LoggerFactory

/**
 * Mina-sshd-netty defaults to debug log level, bringing a lot of very low level logs, To avoid the
 * logback config xml file conflicting and to get a friendly log verbosity level, so be this class
 * based config.
 */
object LogbackConfig {
  private val logbackWarnLevelConfigXml =
    """<?xml version="1.0" encoding="UTF-8"?>
      |<configuration>
      |
      |    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
      |        <filter class="ch.qos.logback.classic.filter.ThresholdFilter">
      |            <level>INFO</level>
      |        </filter>
      |        <!-- <encoder>
      |            <pattern>[%date{ISO8601}] [%level] [%logger] [%marker] [%thread] - %msg {%mdc}%n</pattern>
      |        </encoder> -->
      |    </appender>
      |
      |    <!-- <appender name="FILE" class="ch.qos.logback.core.FileAppender">
      |        <file>target/myapp-dev.log</file>
      |        <encoder>
      |            <pattern>[%date{ISO8601}] [%level] [%logger] [%marker] [%thread] - %msg {%mdc}%n</pattern>
      |        </encoder>
      |    </appender> -->
      |
      |
      |    <root level="DEBUG">
      |        <appender-ref ref="STDOUT"/>
      |        <!-- <appender-ref ref="FILE"/> -->
      |    </root>
      |    <logger name="org.apache.sshd" level="WARN">
      |        <appender-ref ref="STDOUT" />
      |        <!-- <appender-ref ref="FILE"/> -->
      |    </logger>
      |    <logger name="io.netty.bootstrap.Bootstrap" level="ERROR">
      |        <appender-ref ref="STDOUT"/>
      |        <!-- <appender-ref ref="FILE"/> -->
      |    </logger>
      |    <logger name="io.netty" level="WARN">
      |        <appender-ref ref="STDOUT"/>
      |        <!-- <appender-ref ref="FILE"/> -->
      |    </logger>
      |</configuration>
      |""".stripMargin

  private val logbackDebugLevelConfigXml =
    """<?xml version="1.0" encoding="UTF-8"?>
      |<configuration>
      |
      |    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
      |        <filter class="ch.qos.logback.classic.filter.ThresholdFilter">
      |            <level>INFO</level>
      |        </filter>
      |        <!-- <encoder>
      |            <pattern>[%date{ISO8601}] [%level] [%logger] [%marker] [%thread] - %msg {%mdc}%n</pattern>
      |        </encoder> -->
      |    </appender>
      |
      |    <!-- <appender name="FILE" class="ch.qos.logback.core.FileAppender">
      |        <file>target/myapp-dev.log</file>
      |        <encoder>
      |            <pattern>[%date{ISO8601}] [%level] [%logger] [%marker] [%thread] - %msg {%mdc}%n</pattern>
      |        </encoder>
      |    </appender> -->
      |
      |
      |    <root level="DEBUG">
      |        <appender-ref ref="STDOUT"/>
      |        <!-- <appender-ref ref="FILE"/> -->
      |    </root>
      |    <logger name="org.apache.sshd" level="DEBUG">
      |        <appender-ref ref="STDOUT" />
      |        <!-- <appender-ref ref="FILE"/> -->
      |    </logger>
      |    <logger name="io.netty.bootstrap.Bootstrap" level="DEBUG">
      |        <appender-ref ref="STDOUT"/>
      |        <!-- <appender-ref ref="FILE"/> -->
      |    </logger>
      |</configuration>
      |""".stripMargin

  private def configLogbackForLib0(xmlStr: String): Unit = {
    val context = LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext]
    val logbackManualConfigStream = new ByteArrayInputStream(xmlStr.getBytes)
    try {
      val configurator = new JoranConfigurator
      configurator.setContext(context)
      configurator.doConfigure(logbackManualConfigStream)
    } catch {
      case _: JoranException =>
      println("Configuring logback for `cable` failed.")
    } finally {
      logbackManualConfigStream.close()
    }
  }

  def configWarnLogbackForLib(): Unit = {
    configLogbackForLib0(logbackWarnLevelConfigXml)
  }

  def configDebugLogbackForLib(): Unit = {
    configLogbackForLib0(logbackDebugLevelConfigXml)
  }
}
