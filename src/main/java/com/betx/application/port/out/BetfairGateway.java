package com.betx.application.port.out;

import com.betx.domain.betfair.BetfairCredentials;
import com.betx.domain.betfair.BetfairEvent;
import com.betx.domain.betfair.BetfairMarketBook;
import com.betx.domain.betfair.BetfairMarketCatalogue;
import com.betx.domain.betfair.BetfairMarketQuery;
import com.betx.domain.betfair.BetfairSession;
import com.betx.domain.order.BetExecutionResult;
import com.betx.domain.order.BetOrder;
import java.math.BigDecimal;
import java.util.List;

public interface BetfairGateway {
    BetfairSession login(BetfairCredentials credentials);

    default List<BetfairEvent> listEvents(BetfairSession session, List<String> eventTypeIds, List<String> marketTypeCodes) {
        return List.of();
    }

    List<BetfairMarketCatalogue> listMarketCatalogue(BetfairSession session, BetfairMarketQuery query);

    List<BetfairMarketBook> listMarketBook(BetfairSession session, List<String> marketIds);

    default BigDecimal getAccountFunds(BetfairSession session) {
        return null;
    }

    default BetExecutionResult placeOrder(BetfairSession session, BetOrder order) {
        return BetExecutionResult.rejected("Live bet execution is not implemented for configured exchanges.");
    }
}
