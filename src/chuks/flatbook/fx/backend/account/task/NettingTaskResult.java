/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package chuks.flatbook.fx.backend.account.task;

/**
 *
 * @author user
 */
public class NettingTaskResult {

    private final boolean success;
    private final String result;
    
    public NettingTaskResult(boolean success, String result){
        this.success = success;
        this.result = result;
    }
    
    public boolean isSuccess(){
        return success;
    }
    
    
    public String getResult(){
        return result;
    }
}
