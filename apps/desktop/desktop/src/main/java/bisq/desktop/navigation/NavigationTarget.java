/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.desktop.navigation;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public enum NavigationTarget {
    NONE(),
    ROOT(),

    PRIMARY_STAGE(ROOT, false),

    SPLASH(PRIMARY_STAGE, false),

    /* --------------------------------------------------------------------- */
    // OVERLAY
    /* --------------------------------------------------------------------- */

    OVERLAY(PRIMARY_STAGE, false),

    UNLOCK(OVERLAY, false),
    TAC(OVERLAY, false),
    UPDATER(OVERLAY, false),

    ONBOARDING(OVERLAY, false),
    ONBOARDING_WELCOME(ONBOARDING, false),
    ONBOARDING_GENERATE_NYM(ONBOARDING, false),
    ONBOARDING_PASSWORD(ONBOARDING, false),

    BISQ_EASY_VIDEO(OVERLAY, false),

    BISQ_EASY_TRADE_WIZARD(OVERLAY, false),
    BISQ_EASY_TRADE_WIZARD_DIRECTION_AND_MARKET(BISQ_EASY_TRADE_WIZARD, false),
    BISQ_EASY_TRADE_WIZARD_AMOUNT_AND_PRICE(BISQ_EASY_TRADE_WIZARD, false),
    BISQ_EASY_TRADE_WIZARD_PAYMENT_METHODS(BISQ_EASY_TRADE_WIZARD, false),
    BISQ_EASY_TRADE_WIZARD_TAKE_OFFER_OFFER(BISQ_EASY_TRADE_WIZARD, false),
    BISQ_EASY_TRADE_WIZARD_REVIEW_OFFER(BISQ_EASY_TRADE_WIZARD, false),

    BISQ_EASY_TAKE_OFFER(OVERLAY, false),
    BISQ_EASY_TAKE_OFFER_PRICE(BISQ_EASY_TAKE_OFFER, false),
    BISQ_EASY_TAKE_OFFER_AMOUNT(BISQ_EASY_TAKE_OFFER, false),
    BISQ_EASY_TAKE_OFFER_PAYMENT(BISQ_EASY_TAKE_OFFER, false),
    BISQ_EASY_TAKE_OFFER_REVIEW(BISQ_EASY_TAKE_OFFER, false),

    MU_SIG_CREATE_OFFER(OVERLAY, false),
    MU_SIG_CREATE_OFFER_DIRECTION_AND_MARKET(MU_SIG_CREATE_OFFER, false),
    MU_SIG_CREATE_OFFER_AMOUNT_AND_PRICE(MU_SIG_CREATE_OFFER, false),
    MU_SIG_CREATE_OFFER_PAYMENT_METHODS(MU_SIG_CREATE_OFFER, false),
    MU_SIG_CREATE_OFFER_REVIEW_OFFER(MU_SIG_CREATE_OFFER, false),

    MU_SIG_TAKE_OFFER(OVERLAY, false),
    MU_SIG_TAKE_OFFER_PRICE(MU_SIG_TAKE_OFFER, false),
    MU_SIG_TAKE_OFFER_AMOUNT(MU_SIG_TAKE_OFFER, false),
    MU_SIG_TAKE_OFFER_PAYMENT(MU_SIG_TAKE_OFFER, false),
    MU_SIG_TAKE_OFFER_REVIEW(MU_SIG_TAKE_OFFER, false),

    CREATE_PROFILE(OVERLAY, false),
    CREATE_PROFILE_STEP1(CREATE_PROFILE, false),
    CREATE_PROFILE_STEP2(CREATE_PROFILE, false),

    CREATE_PAYMENT_ACCOUNT(OVERLAY, false),
    CREATE_PAYMENT_ACCOUNT_PAYMENT_METHOD(CREATE_PAYMENT_ACCOUNT, false),
    CREATE_PAYMENT_ACCOUNT_DATA(CREATE_PAYMENT_ACCOUNT, false),
    CREATE_PAYMENT_ACCOUNT_OPTIONS(CREATE_PAYMENT_ACCOUNT, false),
    CREATE_PAYMENT_ACCOUNT_SUMMARY(CREATE_PAYMENT_ACCOUNT, false),

    CREATE_CRYPTO_CURRENCY_ACCOUNT(OVERLAY, false),
    CREATE_CRYPTO_CURRENCY_ACCOUNT_CURRENCY(CREATE_CRYPTO_CURRENCY_ACCOUNT, false),
    CREATE_CRYPTO_CURRENCY_ACCOUNT_DATA(CREATE_CRYPTO_CURRENCY_ACCOUNT, false),
    CREATE_CRYPTO_CURRENCY_ACCOUNT_SUMMARY(CREATE_CRYPTO_CURRENCY_ACCOUNT, false),

    CREATE_PAYMENT_ACCOUNT_LEGACY(OVERLAY, false),

    BISQ_EASY_GUIDE(OVERLAY, false),
    BISQ_EASY_GUIDE_WELCOME(BISQ_EASY_GUIDE, false),
    BISQ_EASY_GUIDE_SECURITY(BISQ_EASY_GUIDE, false),
    BISQ_EASY_GUIDE_PROCESS(BISQ_EASY_GUIDE, false),
    BISQ_EASY_GUIDE_RULES(BISQ_EASY_GUIDE, false),

    CHAT_RULES(OVERLAY, false),

    WALLET_GUIDE(OVERLAY, false),
    WALLET_GUIDE_INTRO(WALLET_GUIDE, false),
    WALLET_GUIDE_DOWNLOAD(WALLET_GUIDE, false),
    WALLET_GUIDE_CREATE_WALLET(WALLET_GUIDE, false),
    WALLET_GUIDE_RECEIVE(WALLET_GUIDE, false),

    BISQ_EASY_OFFER_DETAILS(OVERLAY, false),

    BISQ_EASY_TRADE_DETAILS(OVERLAY, false),
    MU_SIG_TRADE_DETAILS(OVERLAY, false),

    MEDIATION_CASE_DETAILS(OVERLAY, false),

    REPORT_TO_MODERATOR(OVERLAY, false),

    BURN_BSQ(OVERLAY, false),
    BURN_BSQ_TAB_1(BURN_BSQ, false),
    BURN_BSQ_TAB_2(BURN_BSQ, false),
    BURN_BSQ_TAB_3(BURN_BSQ, false),

    BSQ_BOND(OVERLAY, false),
    BSQ_BOND_TAB_1(BSQ_BOND, false),
    BSQ_BOND_TAB_2(BSQ_BOND, false),
    BSQ_BOND_TAB_3(BSQ_BOND, false),

    ACCOUNT_AGE(OVERLAY, false),
    ACCOUNT_AGE_TAB_1(ACCOUNT_AGE, false),
    ACCOUNT_AGE_TAB_2(ACCOUNT_AGE, false),
    ACCOUNT_AGE_TAB_3(ACCOUNT_AGE, false),

    SIGNED_WITNESS(OVERLAY, false),
    SIGNED_WITNESS_TAB_1(SIGNED_WITNESS, false),
    SIGNED_WITNESS_TAB_2(SIGNED_WITNESS, false),
    SIGNED_WITNESS_TAB_3(SIGNED_WITNESS, false),

    PROFILE_CARD(OVERLAY, false),
    PROFILE_CARD_DETAILS(PROFILE_CARD, false),
    PROFILE_CARD_OVERVIEW(PROFILE_CARD, false),
    PROFILE_CARD_REPUTATION(PROFILE_CARD, false),
    PROFILE_CARD_OFFERS(PROFILE_CARD, false),
    PROFILE_CARD_MESSAGES(PROFILE_CARD, false),


    /* --------------------------------------------------------------------- */
    // MAIN
    /* --------------------------------------------------------------------- */

    MAIN(PRIMARY_STAGE, false),

    CONTENT(MAIN, false),

    DASHBOARD(CONTENT),

    // BISQ_EASY
    BISQ_EASY(CONTENT),
    BISQ_EASY_ONBOARDING(BISQ_EASY),
    BISQ_EASY_OFFERBOOK(BISQ_EASY),
    BISQ_EASY_OPEN_TRADES(BISQ_EASY),
    BISQ_EASY_PRIVATE_CHAT(BISQ_EASY),

    // MU_SIG
    MU_SIG(CONTENT),
    MU_SIG_OFFERBOOK(MU_SIG),
    MU_SIG_MY_OFFERS(MU_SIG),
    MU_SIG_OPEN_TRADES(MU_SIG),
    MU_SIG_HISTORY(MU_SIG),

    // REPUTATION
    REPUTATION(CONTENT),
    BUILD_REPUTATION(REPUTATION),
    REPUTATION_RANKING(REPUTATION),
    REPUTATION_SCORE(REPUTATION),

    // TRADE_PROTOCOLS
    TRADE_PROTOCOLS(CONTENT),
    TRADE_PROTOCOLS_OVERVIEW(TRADE_PROTOCOLS),
    BISQ_EASY_INFO(TRADE_PROTOCOLS),
    MU_SIG_PROTOCOL(TRADE_PROTOCOLS),
    SUBMARINE(TRADE_PROTOCOLS),
    BISQ_LIGHTNING(TRADE_PROTOCOLS),
    MORE_TRADE_PROTOCOLS(TRADE_PROTOCOLS, false),

    // ACADEMY
    ACADEMY(CONTENT),
    OVERVIEW_ACADEMY(ACADEMY),
    BISQ_ACADEMY(ACADEMY),
    BITCOIN_ACADEMY(ACADEMY),
    SECURITY_ACADEMY(ACADEMY),
    PRIVACY_ACADEMY(ACADEMY),
    WALLETS_ACADEMY(ACADEMY),
    FOSS_ACADEMY(ACADEMY),

    // CHAT
    CHAT(CONTENT),
    CHAT_DISCUSSION(CHAT),
    CHAT_PRIVATE(CHAT),

    // SUPPORT
    SUPPORT(CONTENT),
    SUPPORT_ASSISTANCE(SUPPORT),
    SUPPORT_RESOURCES(SUPPORT),

    // USER
    USER(CONTENT),
    USER_PROFILE(USER),
    PASSWORD(USER),
    FIAT_PAYMENT_ACCOUNTS(USER),
    CRYPTO_CURRENCY_ACCOUNTS(USER),


    // NETWORK
    NETWORK(CONTENT),
    MY_NETWORK_NODE(NETWORK),
    P2P_NETWORK(NETWORK),
    ROLES(NETWORK),
    NODES(NETWORK),

    ROLES_TABS(ROLES),
    REGISTER_MEDIATOR(ROLES_TABS),
    REGISTER_ARBITRATOR(ROLES_TABS),
    REGISTER_MODERATOR(ROLES_TABS),
    REGISTER_SECURITY_MANAGER(ROLES_TABS),
    REGISTER_RELEASE_MANAGER(ROLES_TABS),

    NODES_TABS(NODES),
    REGISTER_SEED_NODE(NODES_TABS),
    REGISTER_ORACLE_NODE(NODES_TABS),
    REGISTER_EXPLORER_NODE(NODES_TABS),
    REGISTER_MARKET_PRICE_NODE(NODES_TABS),

    // SETTINGS
    SETTINGS(CONTENT),
    LANGUAGE_SETTINGS(SETTINGS),
    NOTIFICATION_SETTINGS(SETTINGS),
    DISPLAY_SETTINGS(SETTINGS),
    TRADE_SETTINGS(SETTINGS),
    MISC_SETTINGS(SETTINGS),

    // WALLET
    WALLET(CONTENT),
    WALLET_DASHBOARD(WALLET),
    WALLET_SEND(WALLET),
    WALLET_RECEIVE(WALLET),
    WALLET_TXS(WALLET),
    WALLET_SETTINGS(WALLET),

    // CREATE WALLET
    CREATE_WALLET(OVERLAY, false),
    CREATE_WALLET_PROTECT(CREATE_WALLET, false),
    CREATE_WALLET_BACKUP(CREATE_WALLET, false),
    CREATE_WALLET_VERIFY(CREATE_WALLET, false),

    // AUTHORIZED_ROLE
    AUTHORIZED_ROLE(CONTENT),
    MEDIATOR(AUTHORIZED_ROLE),
    ARBITRATOR(AUTHORIZED_ROLE),
    MODERATOR(AUTHORIZED_ROLE),
    SECURITY_MANAGER(AUTHORIZED_ROLE),
    RELEASE_MANAGER(AUTHORIZED_ROLE),
    SEED_NODE(AUTHORIZED_ROLE),
    ORACLE_NODE(AUTHORIZED_ROLE),
    EXPLORER_NODE(AUTHORIZED_ROLE),
    MARKET_PRICE_NODE(AUTHORIZED_ROLE);

    @Getter
    private final Optional<NavigationTarget> parent;
    @Getter
    private final List<NavigationTarget> path;
    @Getter
    private final boolean allowPersistence;

    NavigationTarget() {
        parent = Optional.empty();
        path = new ArrayList<>();
        allowPersistence = true;
    }

    NavigationTarget(NavigationTarget parent) {
        this(parent, true);
    }

    NavigationTarget(NavigationTarget parent, boolean allowPersistence) {
        this.parent = Optional.of(parent);
        List<NavigationTarget> temp = new ArrayList<>();
        Optional<NavigationTarget> candidate = Optional.of(parent);
        while (candidate.isPresent()) {
            temp.add(0, candidate.get());
            candidate = candidate.get().getParent();
        }
        this.path = temp;
        this.allowPersistence = allowPersistence;
    }
}