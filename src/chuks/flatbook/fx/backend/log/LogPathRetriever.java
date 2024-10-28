/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package chuks.flatbook.fx.backend.log;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.spi.ScanException;
import ch.qos.logback.core.util.OptionHelper;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author user
 */
public class LogPathRetriever {
    
    
    
    public static String getLogFilePath(String appenderName) {
        
        System.out.println(appenderName);
        
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Appender<ILoggingEvent> appender = context.getLogger("ROOT").getAppender(appenderName);
        
        if (appender instanceof FileAppender) {
            return ((FileAppender<ILoggingEvent>) appender).getFile();
        }
        return null;
    }

    public static void main(String[] args) {
        String infoLogPath = getLogFilePath("infoFile"); // Name of the appender
        System.out.println("Info Log File Path: " + infoLogPath);

        // You can repeat for other appenders if needed
        String errorLogPath = getLogFilePath("errorFile");
        System.out.println("Error Log File Path: " + errorLogPath);
    }
}

