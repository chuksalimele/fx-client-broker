/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package chuks.flatbook.fx.backend.listener;

import chuks.flatbook.fx.backend.account.contract.Identifier;

/**
 *
 * @author user
 */
public abstract class ConnectionAdapter implements ConnectionListener {

    private Identifier idf = new Identifier() {
        @Override
        public int getAccountNumber() {
            throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
        }
    };

    public ConnectionAdapter() {
    }

    @Override
    public Identifier onConnectionProgress(String status) {
        return idf;
    }

    @Override
    public Identifier onPriceSessionLogOut() {
        return idf;
    }

    @Override
    public Identifier onQuoteSessionLogOn() {
        return idf;
    }

    @Override
    public Identifier onTradeSessionLogOn() {
        return idf;
    }

    @Override
    public Identifier onTradeSessionLogOut() {
        return idf;
    }

}
