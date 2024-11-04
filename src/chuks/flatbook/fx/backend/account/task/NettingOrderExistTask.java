/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package chuks.flatbook.fx.backend.account.task;

import chuks.flatbook.fx.backend.account.type.OrderNettingAccount;
import chuks.flatbook.fx.common.account.order.Position;
import chuks.flatbook.fx.common.account.order.UnfilledOrder;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.slf4j.LoggerFactory;
import quickfix.ConfigError;
import quickfix.Session;
import quickfix.SessionNotFound;
import quickfix.StringField;
import quickfix.field.Account;
import quickfix.field.AccountType;
import quickfix.field.ClearingBusinessDate;
import quickfix.field.PosReqID;
import quickfix.field.PosReqType;
import quickfix.field.TransactTime;
import quickfix.fix44.RequestForPositions;

/**
 *
 * @author user
 */
public class NettingOrderExistTask extends NettingTask{
    

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(NettingOrderExistTask.class.getName());
    private final String orderID;
    private boolean foundPosition = false;
    private int orderIndex = -1;
    

    public NettingOrderExistTask(OrderNettingAccount account, String identifier, String orderID) {
        super(account, identifier);
        this.orderID = orderID;
    }

    @Override
    public void onOrderReport(UnfilledOrder unfilledOrder, int totalOrders) {
        orderIndex++;        
        if(unfilledOrder.getID().equals(orderID)){       
            foundPosition = true;
            future.complete(new NettingTaskResult(true, "Order exist"));        
        }
        if(orderIndex == totalOrders - 1 && !foundPosition){
            future.complete(new NettingTaskResult(false, "Order does not exist"));            
        }
    }

    
    
    
    
    @Override
    public CompletableFuture<NettingTaskResult> run() {
        try {
            RequestForPositions request = new RequestForPositions();
            request.set(new PosReqID("open-positions-" + System.currentTimeMillis())); // Unique request ID
            request.set(new PosReqType(PosReqType.POSITIONS)); // Request type for positions
            request.set(new Account(OrderNettingAccount.getSettings().getString("Account"))); //The account for which positions are requested
            request.setField(new StringField(715, "CURRENT"));//According to LP doc : ClearingBusinessDate, Local DateTime – currently not used ‘CURRENT’ or any other text will fit the requirements
            request.set(new AccountType(AccountType.ACCOUNT_IS_CARRIED_ON_CUSTOMER_SIDE_OF_THE_BOOKS));
            request.set(new ClearingBusinessDate());
            request.set(new TransactTime());

            Session.sendToTarget(request, account.getTradingSessionID());
        } catch (ConfigError | SessionNotFound ex) {
            String errStr = "Could not request for positions";
            logger.error(errStr +" - "+ex.getMessage(), ex);
            future.complete(new NettingTaskResult(false, errStr));              
        }
        
        return future;
    }
    
}

