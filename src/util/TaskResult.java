/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package util;

/**
 *
 * @author user
 */
public class TaskResult {

    private final boolean success;
    private final String result;
    
    public TaskResult(boolean success, String result){
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
