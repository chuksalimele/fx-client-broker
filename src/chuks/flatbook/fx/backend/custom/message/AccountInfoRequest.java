/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package chuks.flatbook.fx.backend.custom.message;

import quickfix.Message;
import quickfix.StringField;
import quickfix.field.MsgType;

/**
 *
 * @author user
 */
public class AccountInfoRequest extends Message{
    public static final String MSGTYPE_UAA = "UAA";
    
    public AccountInfoRequest() {
        getHeader().setField(new MsgType(MSGTYPE_UAA));        
    }
    
    public void setAccountInfoReqID(String account_info_req_id){
        setField(new StringField(3336, account_info_req_id)); 
    }

    public void setAccount(String acount){
        setField(new StringField(1, acount)); 
    }

}
