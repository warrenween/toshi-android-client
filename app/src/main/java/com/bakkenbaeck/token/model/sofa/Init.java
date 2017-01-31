package com.bakkenbaeck.token.model.sofa;

import com.bakkenbaeck.token.util.LocaleUtil;

public class Init {
    private String paymentAddress;
    private String language;

    public Init construct(final InitRequest initRequest, final String paymentAddress) {
        for (String value : initRequest.getValues()) {
            switch (value) {
                case SofaType.LANGUAGE: {
                    this.language = LocaleUtil.getLocale().getLanguage();
                    break;
                }
                case SofaType.PAYMENT_ADDRESS: {
                    this.paymentAddress = paymentAddress;
                }
            }
        }

        return this;
    }
}
