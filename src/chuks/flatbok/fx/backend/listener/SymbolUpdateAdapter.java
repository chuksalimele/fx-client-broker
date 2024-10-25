/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package chuks.flatbok.fx.backend.listener;

import chuks.flatbok.fx.backend.account.contract.Identifier;
import chuks.flatbok.fx.common.account.order.SymbolInfo;
import java.awt.Component;
import java.util.List;
import java.util.Set;

/**
 *
 * @author user
 */
public abstract class SymbolUpdateAdapter implements SymbolUpdateListener{

    private Component comp;
    private Identifier idf = new Identifier(){
            @Override
            public int getAccountNumber() {
                throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
            }
        };
    public SymbolUpdateAdapter() {
    }

    public SymbolUpdateAdapter(Component comp) {
        this.comp = comp;
    }
    
    public Component getComponent(){
        return comp;
    }
    
    @Override
    public Identifier onSwapChange(SymbolInfo symbolInfo) {        
        return idf;
    }

    @Override
    public Identifier onPriceChange(SymbolInfo symbolInfo) {        
        return idf;
    }

    @Override
    public Identifier onFullSymbolList(int account_number, Set<String> symbol_list) {
        return idf;
    }

    @Override
    public Identifier onSelectedSymbolInfoList(int account_number, List<SymbolInfo> symbol_info_list) {
        return idf;
    }

}
