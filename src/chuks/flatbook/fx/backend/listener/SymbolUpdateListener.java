/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package chuks.flatbook.fx.backend.listener;

import chuks.flatbook.fx.backend.account.contract.Identifier;
import chuks.flatbook.fx.common.account.order.SymbolInfo;
import java.util.List;
import java.util.Set;

/**
 *
 * @author user
 */
public interface SymbolUpdateListener {
    
    public Identifier onSwapChange(SymbolInfo symbolInfo);
    public Identifier onPriceChange(SymbolInfo symbolInfo);
    public Identifier onFullSymbolList(int account_number, Set<String> symbol_list);
    public Identifier onSelectedSymbolInfoList(int account_number, List<SymbolInfo> symbol_info_list);
}
