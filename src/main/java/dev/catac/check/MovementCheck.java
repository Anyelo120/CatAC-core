package dev.catac.check;

import dev.catac.state.MovementFrame;
import dev.catac.state.PlayerData;

public interface MovementCheck extends CatCheck {
    CheckResult evaluate(MovementFrame frame, PlayerData data);
}
