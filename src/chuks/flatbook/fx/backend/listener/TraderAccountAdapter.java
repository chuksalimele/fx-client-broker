/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package chuks.flatbook.fx.backend.listener;

import chuks.flatbook.fx.backend.account.contract.Identifier;
import chuks.flatbook.fx.common.account.profile.UserType;

/**
 *
 * @author user
 */
abstract public class TraderAccountAdapter implements AccountListener{

    private Identifier idf = new Identifier() {
        @Override
        public int getAccountNumber() {
            throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
        }
    };

    @Override
    public Identifier onAccountOpen(int account_number) {
        return idf;
    }

    @Override
    public Identifier onAccountClosed(String email) {
        return idf;
    }

    
    @Override
    public Identifier onAccountApproved(String email) {
        return idf;
    }

    @Override
    public Identifier onAccountActivated(String email) {
        return idf;
    }

    @Override
    public Identifier onAccountDeactivated(String email) {
        return idf;
    }
    
    @Override
    public Identifier onAccountDisabled(String email) {
        return idf;
    }

    @Override
    public Identifier onAccountEnabled(String email) {
        return idf;
    }

    @Override
    public Identifier onLogIn(int account_number) {
        return idf;
    }

    @Override
    public Identifier onLogInFail(int account_number, String reason) {
        return idf;
    }

    @Override
    public Identifier onLogOut(int account_number) {
        return idf;
    }

    @Override
    public Identifier onLogOutFail(int account_number, String reason) {
        return idf;
    }

    @Override
    public Identifier onPasswordChanged(int account_number, char[] new_password) {
        return idf;
    }

    @Override
    public Identifier onSignUpFail(String reason) {
        return idf;
    }

    @Override
    public Identifier onSignUpInitiated(String email) {
        return idf;
    }
    
    
}
