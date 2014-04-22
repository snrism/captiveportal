
package org.sdnhub.learningswitch;

import java.util.Map;
import java.util.UUID;

import org.opendaylight.controller.sal.utils.Status;

public interface ILearningSwitch {
    public UUID createData(LearningSwitchData datum);
    public LearningSwitchData readData(UUID uuid);
    public Map<UUID, LearningSwitchData> readData();
    public Status updateData(UUID uuid, LearningSwitchData data);
    public Status deleteData(UUID uuid);
	public String toggleSwitchHub();
}
