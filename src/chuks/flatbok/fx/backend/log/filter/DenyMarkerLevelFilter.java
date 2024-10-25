/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package chuks.flatbok.fx.backend.log.filter;


import static ch.qos.logback.classic.Level.INFO;
import static ch.qos.logback.classic.Level.WARN;
import static ch.qos.logback.classic.Level.DEBUG;
import static ch.qos.logback.classic.Level.ERROR;
import static ch.qos.logback.classic.Level.TRACE;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;

/**
 *
 * @author user
 */
public class DenyMarkerLevelFilter extends Filter<ILoggingEvent> {
    int level;
    
    public void setLevel(String str_level){
        if(str_level.equals(INFO.levelStr)){
            this.level = INFO.levelInt;
        }else if(str_level.equals(WARN.levelStr)){
            this.level = WARN.levelInt;
        }else if(str_level.equals(DEBUG.levelStr)){
            this.level = DEBUG.levelInt;
        }else if(str_level.equals(ERROR.levelStr)){
            this.level = ERROR.levelInt;
        }else if(str_level.equals(TRACE.levelStr)){
            this.level = TRACE.levelInt;
        }
    }
    
    @Override
    public FilterReply decide(ILoggingEvent event) {
        
        // Do not log if it is not same level
        if(event.getLevel().levelInt != level){
            return FilterReply.DENY;
        }
        
        // Do not log if it has marker
        if (event.getMarker() != null) {
            return FilterReply.DENY;
        }
        
        // At this point it is the same level and
        // does not have a marker so log
        return FilterReply.ACCEPT;
    }
}

