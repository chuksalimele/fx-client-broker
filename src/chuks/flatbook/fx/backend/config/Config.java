/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package chuks.flatbook.fx.backend.config;

import chuks.flatbook.fx.backend.log.LogPathRetriever;
import static chuks.flatbook.fx.backend.log.LogPathRetriever.getLogFilePath;
import java.io.File;
import java.nio.file.Path;

/**
 *
 * @author user
 */
public class Config {

    public final static String TRADE_SESSION_TARGET_COMP_ID = "QFXTRADES";
    public final static String PRICE_SESSION_TARGET_COMP_ID = "QFXPRICES";
    public final static int MINIMUM_TP_SL_PRICE_AWAY = 5;
    public final static int MINIMUM_PENDING_STOP_LIMIT_PRICE_AWAY = 5;
    public static String[] DEFAULT_SYMBOLS
            = {"EURUSD",
                "GBPUSD",
                "AUDUSD",
                "NZDUSD",
                "USDCHF"};
    
    public static final String INFO_LOG_FILE = getLogFilePath("infoFile");
    public static final String WARN_LOG_FILE= getLogFilePath("warnFile");
    public static final String DEBUG_LOG_FILE= getLogFilePath("debugFile");
    public static final String ERROR_LOG_FILE= getLogFilePath("errorFile");
    public static final String TRACE_LOG_FILE= getLogFilePath("traceFile");
    public static final String REJECTED_IPS_LOG_FILE= getLogFilePath("rejectedIPsFile");
    public static final String SUSPICIOUS_IPS_LOG_FILE= getLogFilePath("suspiciousIPsFile");
    //Since all logs are in the same folder lets pick Info dir to represent all
    public static final Path LOG_DIR = new File(Config.INFO_LOG_FILE).getParentFile().toPath();

}
