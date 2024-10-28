/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package chuks.flatbook.fx.backend.log.filter;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;
import org.slf4j.Marker;

/**
 *
 * @author user
 */
public class AcceptMarkerFilter extends Filter<ILoggingEvent> {

    private String markerName;

    /**
     * The setter method name must match with the name on the XML
     * otherwise it will not be called. So if for example the 
     * setter method name is setMyMethodName the child tag of filter
     * tag element defining this class must bear the name myMethodName
     * for the method to be called.
     * @param markerName 
     */
    public void setMarkerName(String markerName) {
        this.markerName = markerName;
    }

    @Override
    public FilterReply decide(ILoggingEvent event) {
        Marker marker = event.getMarker();
        if (marker != null && markerName.equals(marker.getName())) {
            return FilterReply.ACCEPT;
        }
        return FilterReply.DENY;
    }
}

