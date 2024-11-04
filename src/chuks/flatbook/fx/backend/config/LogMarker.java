/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package chuks.flatbook.fx.backend.config;

import static chuks.flatbook.fx.common.util.log.LogConst.STR_INCOMPLETE_TRANSACTION;
import static chuks.flatbook.fx.common.util.log.LogConst.STR_REJECTED_IP;
import static chuks.flatbook.fx.common.util.log.LogConst.STR_SUSPICIOUS_IP;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

/**
 *
 * @author user
 */
public interface LogMarker {
    
    // Create a Marker for REJECTS IP logs
    static final Marker MARKER_REJECTED_IP = MarkerFactory.getMarker(STR_REJECTED_IP);
    
    // Create a Marker for REJECTS IP logs
    static final Marker MARKER_SUSPICIOUS_IP = MarkerFactory.getMarker(STR_SUSPICIOUS_IP);    
    
    // Create a Marker for INCOMPLETE TRANSACTION logs
    static final Marker INCOMPLETE_TRANSACTION = MarkerFactory.getMarker(STR_INCOMPLETE_TRANSACTION);    

}
