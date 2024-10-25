/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package chuks.flatbok.fx.backend.config;

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

}
