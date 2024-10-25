/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package chuks.flatbok.fx.backend.account.contract;

import chuks.flatbok.fx.backend.account.type.HedgeAccount.Builder;
import quickfix.ConfigError;

/**
 *
 * @author user
 */
public interface RetailAccountBuilder {

    public Builder accountConfig(String settings_filename) throws ConfigError ;
}
