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

package bisq.desktop.main.content.mu_sig.open_trades;

import bisq.desktop.CssConfig;
import bisq.desktop.common.Layout;
import bisq.desktop.common.threading.UIThread;
import bisq.desktop.common.utils.ImageUtil;
import bisq.desktop.components.containers.Spacer;
import bisq.desktop.components.controls.Badge;
import bisq.desktop.components.controls.BisqTooltip;
import bisq.desktop.components.controls.BitcoinAmountDisplay;
import bisq.desktop.components.table.BisqTableColumn;
import bisq.desktop.components.table.BisqTableView;
import bisq.desktop.components.table.DateColumnUtil;
import bisq.desktop.main.content.chat.ChatView;
import bisq.desktop.main.content.components.UserProfileDisplay;
import bisq.desktop.main.content.components.UserProfileIcon;
import bisq.i18n.Res;
import bisq.user.profile.UserProfile;
import javafx.beans.InvalidationListener;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Callback;
import lombok.extern.slf4j.Slf4j;
import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;

import java.util.Comparator;

@Slf4j
public final class MuSigOpenTradesView extends ChatView<MuSigOpenTradesView, MuSigOpenTradesModel> {
    private final VBox tradeWelcomeViewRoot, tradeStateViewRoot, chatVBox;
    private final BisqTableView<MuSigOpenTradeListItem> tableView;
    private final Button toggleChatWindowButton;
    private final AnchorPane tableViewAnchorPane;
    private Subscription noOpenTradesPin, tradeRulesAcceptedPin, tableViewSelectionPin,
            selectedModelItemPin, chatWindowPin, isAnyTradeInMediationPin;
    private BisqTableColumn<MuSigOpenTradeListItem> mediatorColumn;
    private final InvalidationListener listItemListener;

    public MuSigOpenTradesView(MuSigOpenTradesModel model,
                               MuSigOpenTradesController controller,
                               HBox tradeDataHeader,
                               VBox chatMessagesComponent,
                               Pane channelSidebar,
                               VBox tradeStateViewRoot,
                               VBox tradeWelcomeViewRoot) {
        super(model, controller, chatMessagesComponent, channelSidebar);

        this.tradeStateViewRoot = tradeStateViewRoot;
        this.tradeWelcomeViewRoot = tradeWelcomeViewRoot;

        // Table view
        tableView = new BisqTableView<>(getModel().getSortedList());
        configTableView();

        Label headlineLabel = new Label(Res.get("bisqEasy.openTrades.table.headline"));
        headlineLabel.getStyleClass().add("bisq-easy-container-headline");
        HBox header = new HBox(10, headlineLabel);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("chat-container-header");

        tableViewAnchorPane = new AnchorPane(tableView);
        Layout.pinToAnchorPane(tableView, 0, 0, 0, 0);

        VBox.setVgrow(tableViewAnchorPane, Priority.ALWAYS);
        VBox tableViewVBox = new VBox(header, Layout.hLine(), tableViewAnchorPane);
        tableViewVBox.setFillWidth(true);
        tableViewVBox.getStyleClass().add("bisq-easy-container");

        // ChatBox
        toggleChatWindowButton = new Button();
        toggleChatWindowButton.setGraphicTextGap(10);
        toggleChatWindowButton.getStyleClass().add("outlined-button");
        toggleChatWindowButton.setMinWidth(140);

        tradeDataHeader.getChildren().addAll(Spacer.fillHBox(), toggleChatWindowButton);

        chatMessagesComponent.setMinHeight(200);
        chatMessagesComponent.setPadding(new Insets(0, -30, -15, -30));

        VBox.setMargin(chatMessagesComponent, new Insets(0, 30, 15, 30));
        VBox.setVgrow(chatMessagesComponent, Priority.ALWAYS);

        chatVBox = new VBox(tradeDataHeader, Layout.hLine(), chatMessagesComponent);
        chatVBox.setAlignment(Pos.CENTER);
        chatVBox.getStyleClass().add("bisq-easy-container");

        VBox.setMargin(tradeWelcomeViewRoot, new Insets(0, 0, 10, 0));
        VBox.setMargin(tableViewVBox, new Insets(0, 0, 10, 0));
        VBox.setMargin(tradeStateViewRoot, new Insets(0, 0, 10, 0));
        VBox.setVgrow(tradeStateViewRoot, Priority.ALWAYS);
        VBox.setVgrow(chatVBox, Priority.ALWAYS);
        VBox.setVgrow(tableViewVBox, Priority.NEVER);
        centerVBox.getChildren().addAll(tradeWelcomeViewRoot, tableViewVBox, tradeStateViewRoot, chatVBox);

        listItemListener = o -> numListItemsChanged();
    }

    @Override
    protected void configTitleHBox() {
    }

    @Override
    protected void configCenterVBox() {
        centerVBox.setAlignment(Pos.TOP_CENTER);
        centerVBox.setFillWidth(true);
    }

    @Override
    protected void onViewAttached() {
        super.onViewAttached();

        tableView.getItems().addListener(listItemListener);

        tableView.initialize();

        MuSigOpenTradesModel model = getModel();

        tradeWelcomeViewRoot.visibleProperty().bind(model.getTradeWelcomeVisible());
        tradeWelcomeViewRoot.managedProperty().bind(model.getTradeWelcomeVisible());
        tradeStateViewRoot.visibleProperty().bind(model.getTradeStateVisible());
        tradeStateViewRoot.managedProperty().bind(model.getTradeStateVisible());
        chatVBox.visibleProperty().bind(model.getChatVisible());
        chatVBox.managedProperty().bind(model.getChatVisible());

        selectedModelItemPin = EasyBind.subscribe(model.getSelectedItem(), selected ->
                tableView.getSelectionModel().select(selected));

        tradeRulesAcceptedPin = EasyBind.subscribe(model.getTradeRulesAccepted(),
                tradeRulesAccepted -> {
                    if (tradeRulesAccepted) {
                        tableView.setOnMouseClicked(null);
                        tableViewSelectionPin = EasyBind.subscribe(tableView.getSelectionModel().selectedItemProperty(),
                                item -> {
                                    if (item != null) {
                                        getController().onSelectItem(item);
                                    }
                                });
                        UIThread.runOnNextRenderFrame(() -> tradeRulesAcceptedPin.unsubscribe());
                    } else {
                        tableView.setOnMouseClicked(e -> getController().onShowTradeRulesAcceptedWarning());
                    }
                });
        noOpenTradesPin = EasyBind.subscribe(model.getNoOpenTrades(),
                noOpenTrades -> {
                    if (noOpenTrades) {
                        tableView.removeListeners();
                        tableView.setPlaceholderText(Res.get("bisqEasy.openTrades.noTrades"));
                        tableView.allowVerticalScrollbar();
                        tableViewAnchorPane.setMinHeight(150);
                        tableViewAnchorPane.setMaxHeight(150);
                        tableView.setMinWidth(500);
                        tableView.getStyleClass().add("empty-table");
                    } else {
                        tableView.setPlaceholder(null);
                        tableView.getStyleClass().remove("empty-table");
                    }
                });

        chatWindowPin = EasyBind.subscribe(model.getChatWindow(), this::chatWindowChanged);

        isAnyTradeInMediationPin = EasyBind.subscribe(model.getIsAnyTradeInMediation(), isAnyTradeInMediation -> {
            if (isAnyTradeInMediation == null) {
                return;
            }
            if (isAnyTradeInMediation && !tableView.getColumns().contains(mediatorColumn)) {
                tableView.getColumns().add(4, mediatorColumn);
            } else {
                tableView.getColumns().remove(mediatorColumn);
            }
        });

        toggleChatWindowButton.setOnAction(e -> getController().onToggleChatWindow());
        numListItemsChanged();
    }

    @Override
    protected void onViewDetached() {
        super.onViewDetached();

        // TODO would be nice to keep it open or allow multiple windows... but for now keep it simple...
        getController().onCloseChatWindow();

        tableView.getItems().removeListener(listItemListener);
        tableView.dispose();

        tradeWelcomeViewRoot.visibleProperty().unbind();
        tradeWelcomeViewRoot.managedProperty().unbind();
        tradeStateViewRoot.visibleProperty().unbind();
        tradeStateViewRoot.managedProperty().unbind();
        chatVBox.visibleProperty().unbind();
        chatVBox.managedProperty().unbind();

        selectedModelItemPin.unsubscribe();
        if (tableViewSelectionPin != null) {
            tableViewSelectionPin.unsubscribe();
        }
        noOpenTradesPin.unsubscribe();
        tradeRulesAcceptedPin.unsubscribe();
        chatWindowPin.unsubscribe();
        isAnyTradeInMediationPin.unsubscribe();

        toggleChatWindowButton.setOnAction(null);
        tableView.setOnMouseClicked(null);
    }

    private void numListItemsChanged() {
        if (tableView.getItems().isEmpty()) {
            return;
        }
        double height = tableView.calculateTableHeight(3);
        tableViewAnchorPane.setMinHeight(height + 1);
        tableViewAnchorPane.setMaxHeight(height + 1);
        UIThread.runOnNextRenderFrame(() -> {
            tableViewAnchorPane.setMinHeight(height);
            tableViewAnchorPane.setMaxHeight(height);
            // Delay call as otherwise the width does not take the scrollbar width correctly into account
            UIThread.runOnNextRenderFrame(tableView::adjustMinWidth);
        });
    }

    private void chatWindowChanged(Stage chatWindow) {
        if (chatWindow == null) {
            ImageView icon = ImageUtil.getImageViewById("detach");
            toggleChatWindowButton.setText(Res.get("bisqEasy.openTrades.chat.detach"));
            toggleChatWindowButton.setTooltip(new BisqTooltip(Res.get("bisqEasy.openTrades.chat.detach.tooltip")));
            toggleChatWindowButton.setGraphic(icon);

            if (!centerVBox.getChildren().contains(chatVBox)) {
                centerVBox.getChildren().add(3, chatVBox);
            }
        } else {
            ImageView icon = ImageUtil.getImageViewById("attach");
            toggleChatWindowButton.setText(Res.get("bisqEasy.openTrades.chat.attach"));
            toggleChatWindowButton.setTooltip(new BisqTooltip(Res.get("bisqEasy.openTrades.chat.attach.tooltip")));
            toggleChatWindowButton.setGraphic(icon);

            chatWindow.titleProperty().bind(getModel().getChatWindowTitle());
            ImageUtil.addAppIcons(chatWindow);
            chatWindow.initModality(Modality.NONE);

            // We open the window at the button position (need to be done before we remove the chatVBox
            // TODO we could persist the position and size of the window and use it for next time opening...
            Point2D windowPoint = new Point2D(root.getScene().getWindow().getX(), root.getScene().getWindow().getY());
            Point2D scenePoint = new Point2D(root.getScene().getX(), root.getScene().getY());
            Point2D buttonPoint = toggleChatWindowButton.localToScene(0.0, 0.0);
            double x = Math.round(windowPoint.getX() + scenePoint.getX() + buttonPoint.getX());
            double y = Math.round(windowPoint.getY() + scenePoint.getY() + buttonPoint.getY());
            chatWindow.setX(x);
            chatWindow.setY(y);
            chatWindow.setMinWidth(600);
            chatWindow.setMinHeight(400);
            chatWindow.setWidth(1000);
            chatWindow.setHeight(700);

            chatWindow.setOnCloseRequest(event -> {
                event.consume();
                chatWindow.titleProperty().unbind();
                getController().onCloseChatWindow();
                chatWindow.hide();
            });

            chatWindow.show();

            centerVBox.getChildren().remove(chatVBox);

            Layout.pinToAnchorPane(chatVBox, 0, 0, 0, 0);
            AnchorPane windowRoot = new AnchorPane(chatVBox);
            windowRoot.getStyleClass().add("bisq-popup");

            Scene scene = new Scene(windowRoot);
            CssConfig.addAllCss(scene);
            chatWindow.setScene(scene);

            // Avoid flicker
            chatWindow.setOpacity(0);
            UIThread.runOnNextRenderFrame(() -> chatWindow.setOpacity(1));
        }
    }

    private void configTableView() {
        tableView.getColumns().add(tableView.getSelectionMarkerColumn());

        tableView.getColumns().add(new BisqTableColumn.Builder<MuSigOpenTradeListItem>()
                .title(Res.get("bisqEasy.openTrades.table.me"))
                .fixWidth(45)
                .left()
                .comparator(Comparator.comparing(MuSigOpenTradeListItem::getMyUserName))
                .setCellFactory(getMyUserCellFactory())
                .build());
        tableView.getColumns().add(new BisqTableColumn.Builder<MuSigOpenTradeListItem>()
                .minWidth(95)
                .left()
                .comparator(Comparator.comparing(MuSigOpenTradeListItem::getDirectionalTitle))
                .valueSupplier(MuSigOpenTradeListItem::getDirectionalTitle)
                .build());
        tableView.getColumns().add(new BisqTableColumn.Builder<MuSigOpenTradeListItem>()
                .title(Res.get("bisqEasy.openTrades.table.tradePeer"))
                .minWidth(110)
                .left()
                .comparator(Comparator.comparing(MuSigOpenTradeListItem::getPeersUserName))
                .setCellFactory(getTradePeerCellFactory())
                .build());

        mediatorColumn = new BisqTableColumn.Builder<MuSigOpenTradeListItem>()
                .title(Res.get("bisqEasy.openTrades.table.mediator"))
                .minWidth(110)
                .left()
                .comparator(Comparator.comparing(MuSigOpenTradeListItem::getMediatorUserName))
                .setCellFactory(getMediatorCellFactory())
                .build();

        tableView.getColumns().add(DateColumnUtil.getDateColumn(tableView.getSortOrder()));

        tableView.getColumns().add(new BisqTableColumn.Builder<MuSigOpenTradeListItem>()
                .title(Res.get("bisqEasy.openTrades.table.tradeId"))
                .minWidth(85)
                .comparator(Comparator.comparing(MuSigOpenTradeListItem::getTradeId))
                .valueSupplier(MuSigOpenTradeListItem::getShortTradeId)
                .tooltipSupplier(MuSigOpenTradeListItem::getTradeId)
                .build());
        tableView.getColumns().add(new BisqTableColumn.Builder<MuSigOpenTradeListItem>()
                .title(Res.get("bisqEasy.openTrades.table.quoteAmount"))
                .fixWidth(120)
                .comparator(Comparator.comparing(MuSigOpenTradeListItem::getQuoteAmount))
                .valueSupplier(MuSigOpenTradeListItem::getQuoteAmountString)
                .build());
        tableView.getColumns().add(new BisqTableColumn.Builder<MuSigOpenTradeListItem>()
                .title(Res.get("bisqEasy.openTrades.table.baseAmount"))
                .fixWidth(120)
                .comparator(Comparator.comparing(MuSigOpenTradeListItem::getBaseAmount))
                .setCellFactory(getBaseCellFactory())
                .build());
        tableView.getColumns().add(new BisqTableColumn.Builder<MuSigOpenTradeListItem>()
                .title(Res.get("bisqEasy.openTrades.table.price"))
                .fixWidth(170)
                .comparator(Comparator.comparing(MuSigOpenTradeListItem::getPrice))
                .valueSupplier(MuSigOpenTradeListItem::getPriceString)
                .build());
        tableView.getColumns().add(new BisqTableColumn.Builder<MuSigOpenTradeListItem>()
                .title(Res.get("bisqEasy.openTrades.table.paymentMethod"))
                .minWidth(60)
                .comparator(Comparator.comparing(MuSigOpenTradeListItem::getFiatPaymentMethod))
                .setCellFactory(getPaymentMethodCellFactory())
                .build());
        tableView.getColumns().add(new BisqTableColumn.Builder<MuSigOpenTradeListItem>()
                .title(Res.get("bisqEasy.openTrades.table.makerTakerRole"))
                .minWidth(85)
                .right()
                .comparator(Comparator.comparing(MuSigOpenTradeListItem::getMyRole))
                .valueSupplier(MuSigOpenTradeListItem::getMyRole)
                .build());
    }

    private Callback<TableColumn<MuSigOpenTradeListItem, MuSigOpenTradeListItem>, TableCell<MuSigOpenTradeListItem, MuSigOpenTradeListItem>> getBaseCellFactory() {
        return column -> new TableCell<>() {
            private final BitcoinAmountDisplay bitcoinAmountDisplay = new BitcoinAmountDisplay("0", false);

            {
                bitcoinAmountDisplay.getSignificantDigits().getStyleClass().add("bisq-easy-open-trades-bitcoin-amount-display");
                bitcoinAmountDisplay.getLeadingZeros().getStyleClass().add("bisq-easy-open-trades-bitcoin-amount-display");
                bitcoinAmountDisplay.getIntegerPart().getStyleClass().add("bisq-easy-open-trades-bitcoin-amount-display");
                bitcoinAmountDisplay.setTranslateY(5);
            }

            @Override
            protected void updateItem(MuSigOpenTradeListItem item, boolean empty) {
                super.updateItem(item, empty);

                if (item != null && !empty) {
                    bitcoinAmountDisplay.applySmallCompactConfig();
                    bitcoinAmountDisplay.setBtcAmount(item.getBaseAmountString());
                    setGraphic(bitcoinAmountDisplay);
                } else {
                    setGraphic(null);
                }
            }
        };
    }

    private Callback<TableColumn<MuSigOpenTradeListItem, MuSigOpenTradeListItem>, TableCell<MuSigOpenTradeListItem, MuSigOpenTradeListItem>> getMyUserCellFactory() {
        return column -> new TableCell<>() {

            private final UserProfileIcon userProfileIcon = new UserProfileIcon();
            private final StackPane stackPane = new StackPane(userProfileIcon);

            @Override
            protected void updateItem(MuSigOpenTradeListItem item, boolean empty) {
                super.updateItem(item, empty);

                if (item != null && !empty) {
                    userProfileIcon.setUserProfile(item.getMyUserProfile(), false);
                    // Tooltip is not working if we add directly to the cell therefor we wrap into a StackPane
                    setGraphic(stackPane);
                } else {
                    userProfileIcon.dispose();
                    setGraphic(null);
                }
            }
        };
    }

    private Callback<TableColumn<MuSigOpenTradeListItem, MuSigOpenTradeListItem>, TableCell<MuSigOpenTradeListItem, MuSigOpenTradeListItem>> getTradePeerCellFactory() {
        return column -> new TableCell<>() {
            private UserProfileDisplay userProfileDisplay;
            private Badge badge;

            @Override
            protected void updateItem(MuSigOpenTradeListItem item, boolean empty) {
                super.updateItem(item, empty);

                if (item != null && !empty) {
                    userProfileDisplay = new UserProfileDisplay(item.getPeersUserProfile(), false);
                    userProfileDisplay.setReputationScore(item.getReputationScore());

                    badge = new Badge(userProfileDisplay);
                    badge.getStyleClass().add("open-trades-badge");
                    badge.setLabelColor("-bisq-black");
                    badge.textProperty().bind(item.getPeerNumNotificationsProperty());
                    badge.setPosition(Pos.BOTTOM_LEFT);
                    badge.setBadgeInsets(new Insets(0, 0, 7.5, 20));
                    // Label color does not get applied from badge style when in a list cell even we use '!important' in the css.
                    badge.getLabel().setStyle("-fx-text-fill: black !important;");
                    setGraphic(badge);
                } else {
                    if (userProfileDisplay != null) {
                        userProfileDisplay.dispose();
                        userProfileDisplay = null;
                    }
                    if (badge != null) {
                        badge.textProperty().unbind();
                    }
                    setGraphic(null);
                }
            }
        };
    }

    private Callback<TableColumn<MuSigOpenTradeListItem, MuSigOpenTradeListItem>, TableCell<MuSigOpenTradeListItem, MuSigOpenTradeListItem>> getMediatorCellFactory() {
        return column -> new TableCell<>() {
            private UserProfileDisplay userProfileDisplay;
            private Badge badge;

            @Override
            protected void updateItem(MuSigOpenTradeListItem item, boolean empty) {
                super.updateItem(item, empty);

                if (item != null && !empty && item.getChannel().getMediator().isPresent()) {
                    UserProfile mediator = item.getChannel().getMediator().get();
                    userProfileDisplay = new UserProfileDisplay(mediator, false);
                    userProfileDisplay.setReputationScore(item.getReputationScore());

                    badge = new Badge(userProfileDisplay);
                    badge.getStyleClass().add("open-trades-badge");
                    badge.textProperty().bind(item.getMediatorNumNotificationsProperty());
                    badge.setPosition(Pos.BOTTOM_LEFT);
                    badge.setBadgeInsets(new Insets(0, 0, 7.5, 20));
                    // Label color does not get applied from badge style when in a list cell even we use '!important' in the css.
                    badge.getLabel().setStyle("-fx-text-fill: black !important;");
                    setGraphic(badge);
                } else {
                    if (userProfileDisplay != null) {
                        userProfileDisplay.dispose();
                        userProfileDisplay = null;
                    }
                    if (badge != null) {
                        badge.textProperty().unbind();
                    }
                    setGraphic(null);
                }
            }
        };
    }

    private Callback<TableColumn<MuSigOpenTradeListItem, MuSigOpenTradeListItem>, TableCell<MuSigOpenTradeListItem, MuSigOpenTradeListItem>> getPaymentMethodCellFactory() {
        return column -> new TableCell<>() {
            private final BisqTooltip tooltip = new BisqTooltip(BisqTooltip.Style.MEDIUM_DARK);
            private final StackPane pane = new StackPane();

            @Override
            protected void updateItem(MuSigOpenTradeListItem item, boolean empty) {
                super.updateItem(item, empty);

                if (item != null && !empty) {
                    Node paymentMethodIcon = !item.isFiatPaymentMethodCustom()
                            ? ImageUtil.getImageViewById(item.getFiatPaymentRail().name())
                            : MuSigViewUtils.getCustomPaymentMethodIcon(item.getFiatPaymentMethod());
                    pane.getChildren().add(paymentMethodIcon);
                    tooltip.setText(Res.get("muSig.openTrades.table.paymentMethod.tooltip",
                            item.getFiatPaymentMethod()));
                    Tooltip.install(pane, tooltip);
                    setGraphic(pane);
                } else {
                    Tooltip.uninstall(pane, tooltip);
                    pane.getChildren().clear();
                    setGraphic(null);
                }
            }
        };
    }

    private MuSigOpenTradesModel getModel() {
        return (MuSigOpenTradesModel) model;
    }

    private MuSigOpenTradesController getController() {
        return (MuSigOpenTradesController) controller;
    }

}
