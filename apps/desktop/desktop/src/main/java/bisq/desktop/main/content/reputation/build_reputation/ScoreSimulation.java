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

package bisq.desktop.main.content.reputation.build_reputation;

import bisq.desktop.components.controls.MaterialTextField;
import bisq.desktop.main.content.reputation.build_reputation.components.AgeSlider;
import bisq.i18n.Res;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;

@Slf4j
public abstract class ScoreSimulation {

    protected final Controller<? extends Model, ? extends View<?, ?>> controller;

    public ScoreSimulation() {
        controller = createController();
    }

    protected abstract Controller<? extends Model, ? extends View<?, ?>> createController();

    public VBox getViewRoot() {
        return controller.getView().getRoot();
    }

    @Slf4j
    protected static abstract class Controller<M extends Model, V extends View<?, ?>> implements bisq.desktop.common.view.Controller {
        @Getter
        protected final V view;
        protected final M model;
        private Subscription agePin, ageAsStringPin;

        protected Controller(int defaultAge, int ageSliderMin, int ageSliderMax) {
            model = createModel(defaultAge, ageSliderMin, ageSliderMax);
            view = createView(model);
        }


        protected abstract M createModel(int defaultAge, int ageSliderMin, int ageSliderMax);

        protected abstract V createView(M model);

        @Override
        public void onActivate() {
            agePin = EasyBind.subscribe(model.getAge(), age -> {
                model.getAgeAsString().set(String.valueOf(age));
                calculateSimScore();
            });
            ageAsStringPin = EasyBind.subscribe(model.getAgeAsString(), ageAsString -> {
                try {
                    model.getAge().set(Integer.parseInt(ageAsString));
                } catch (Exception ignore) {
                }
            });
        }

        @Override
        public void onDeactivate() {
            agePin.unsubscribe();
            ageAsStringPin.unsubscribe();
        }

        protected abstract void calculateSimScore();
    }

    @Getter
    protected static class Model implements bisq.desktop.common.view.Model {
        private final int ageSliderMin;
        private final int ageSliderMax;
        private final IntegerProperty age = new SimpleIntegerProperty();
        private final StringProperty ageAsString = new SimpleStringProperty();
        private final StringProperty score = new SimpleStringProperty();

        public Model(int defaultAge, int ageSliderMin, int ageSliderMax) {
            this.ageSliderMin = ageSliderMin;
            this.ageSliderMax = ageSliderMax;
            age.set(defaultAge);
            ageAsString.set(String.valueOf(defaultAge));
        }
    }

    protected static class View<M extends Model, C extends Controller<?, ?>> extends bisq.desktop.common.view.View<VBox, M, C> {
        private static final double MATERIAL_FIELD_WIDTH = 260;


        private final MaterialTextField score;
        private final AgeSlider simAgeSlider;
        private final MaterialTextField ageField;

        protected View(M model, C controller) {
            super(new VBox(10), model, controller);

            Label simHeadline = new Label(Res.get("reputation.sim.headline"));
            simHeadline.getStyleClass().addAll("bisq-text-1");

            score = getField(Res.get("reputation.sim.score"));
            ageField = getInputField("reputation.sim.age");
            simAgeSlider = new AgeSlider(model.getAgeSliderMin(), model.getAgeSliderMax(), model.getAge().get());

            VBox.setMargin(simAgeSlider.getView().getRoot(), new Insets(15, 0, 0, 0));
            root.getChildren().addAll(simHeadline,
                    ageField,
                    simAgeSlider.getView().getRoot(),
                    score);
        }

        @Override
        protected void onViewAttached() {
            simAgeSlider.valueProperty().bindBidirectional(model.getAge());
            ageField.textProperty().bindBidirectional(model.getAgeAsString());
            score.textProperty().bind(model.getScore());
        }

        @Override
        protected void onViewDetached() {
            simAgeSlider.valueProperty().unbindBidirectional(model.getAge());
            ageField.textProperty().unbindBidirectional(model.getAgeAsString());
            score.textProperty().unbind();
        }

        protected MaterialTextField getField(String description) {
            MaterialTextField field = new MaterialTextField(description);
            field.setEditable(false);
            field.setMinWidth(MATERIAL_FIELD_WIDTH);
            field.setMaxWidth(MATERIAL_FIELD_WIDTH);
            return field;
        }

        protected MaterialTextField getInputField(String key) {
            MaterialTextField field = new MaterialTextField(Res.get(key), Res.get(key + ".prompt"));
            field.setMinWidth(MATERIAL_FIELD_WIDTH);
            field.setMaxWidth(MATERIAL_FIELD_WIDTH);
            return field;
        }
    }
}
