package com.betx.domain.betfair;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class BetfairCountryTest {
    @Test
    void resolvesLoginUrlForEachSupportedCountry() {
        assertThat(BetfairCountry.GLOBAL.loginUrl()).isEqualTo("https://identitysso.betfair.com/api/login");
        assertThat(BetfairCountry.AUSTRALIA_NEW_ZEALAND.loginUrl()).isEqualTo("https://identitysso.betfair.com.au/api/login");
        assertThat(BetfairCountry.ITALY.loginUrl()).isEqualTo("https://identitysso.betfair.it/api/login");
        assertThat(BetfairCountry.SPAIN.loginUrl()).isEqualTo("https://identitysso.betfair.es/api/login");
        assertThat(BetfairCountry.ROMANIA.loginUrl()).isEqualTo("https://identitysso.betfair.ro/api/login");
    }

    @Test
    void parsesYamlFriendlyCountryNames() {
        assertThat(BetfairCountry.fromConfigValue("global")).isEqualTo(BetfairCountry.GLOBAL);
        assertThat(BetfairCountry.fromConfigValue("australia_new_zealand")).isEqualTo(BetfairCountry.AUSTRALIA_NEW_ZEALAND);
        assertThat(BetfairCountry.fromConfigValue("italy")).isEqualTo(BetfairCountry.ITALY);
        assertThat(BetfairCountry.fromConfigValue("spain")).isEqualTo(BetfairCountry.SPAIN);
        assertThat(BetfairCountry.fromConfigValue("romania")).isEqualTo(BetfairCountry.ROMANIA);
    }

    @Test
    void defaultsToSpainWhenCountryIsMissing() {
        assertThat(BetfairCountry.fromConfigValue(null)).isEqualTo(BetfairCountry.SPAIN);
        assertThat(BetfairCountry.fromConfigValue(" ")).isEqualTo(BetfairCountry.SPAIN);
    }

    @Test
    void rejectsUnsupportedCountries() {
        assertThatThrownBy(() -> BetfairCountry.fromConfigValue("france"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unsupported Betfair country");
    }
}
