/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package chuks.flatbok.fx.backend.listener;

import chuks.flatbok.fx.backend.account.contract.Identifier;
import chuks.flatbok.fx.common.account.profile.UserType;

/**
 *
 * @author user
 */
public interface AccountListener {
    Identifier onLogIn(int account_number);
    Identifier onLogInFail(int account_number, String reason);
    Identifier onLogOut(int account_number);
    Identifier onLogOutFail(int account_number, String reason);
    Identifier onAccountOpen(int account_number);
    Identifier onSignUpFail(String reason);
    Identifier onPasswordChanged(int account_number, char[] new_password);
    Identifier onAccountActivated(String email);
    Identifier onAccountDeactivated(String email);    
    Identifier onAccountDisabled(String email);
    Identifier onAccountEnabled(String email);
    Identifier onAccountApproved(String email);
    Identifier onAccountClosed(String email);    
}