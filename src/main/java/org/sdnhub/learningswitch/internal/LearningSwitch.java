
package org.sdnhub.learningswitch.internal;

import org.sdnhub.learningswitch.ILearningSwitch;
import org.sdnhub.learningswitch.LearningSwitchData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.Map;

import org.opendaylight.controller.sal.utils.Status;
import org.opendaylight.controller.sal.utils.StatusCode;
import org.opendaylight.controller.sal.action.Action;
import org.opendaylight.controller.sal.action.Output;
import org.opendaylight.controller.sal.core.ConstructionException;
import org.opendaylight.controller.sal.core.Node;
import org.opendaylight.controller.sal.core.NodeConnector;
import org.opendaylight.controller.sal.flowprogrammer.Flow;
import org.opendaylight.controller.sal.flowprogrammer.IFlowProgrammerService;
import org.opendaylight.controller.sal.match.Match;
import org.opendaylight.controller.sal.match.MatchField;
import org.opendaylight.controller.sal.match.MatchType;
import org.opendaylight.controller.sal.packet.BitBufferHelper;
import org.opendaylight.controller.sal.packet.Ethernet;
import org.opendaylight.controller.sal.packet.IDataPacketService;
import org.opendaylight.controller.sal.packet.IListenDataPacket;
import org.opendaylight.controller.sal.packet.IPv4;
import org.opendaylight.controller.sal.packet.Packet;
import org.opendaylight.controller.sal.packet.PacketResult;
import org.opendaylight.controller.sal.packet.RawPacket;
import org.opendaylight.controller.sal.packet.UDP;
import org.opendaylight.controller.sal.utils.NetUtils;
import org.opendaylight.controller.switchmanager.ISwitchManager;

public class LearningSwitch implements ILearningSwitch, IListenDataPacket {
    private Map<UUID, LearningSwitchData> data;
    protected static final Logger logger = LoggerFactory.getLogger(LearningSwitch.class);
	private IDataPacketService dataPacketService = null;
	private ISwitchManager switchManager = null;
	private IFlowProgrammerService programmer = null;
	private Map<Long, NodeConnector> mac_to_port = new HashMap<Long, NodeConnector>();
	private String function = "hub";

	void setDataPacketService(IDataPacketService s) {
		this.dataPacketService = s;
	}

	void unsetDataPacketService(IDataPacketService s) {
		if (this.dataPacketService == s) {
			this.dataPacketService = null;
		}
	}

	public void setFlowProgrammerService(IFlowProgrammerService s)
	{
		this.programmer = s;
	}

	public void unsetFlowProgrammerService(IFlowProgrammerService s) {
		if (this.programmer == s) {
			this.programmer = null;
		}
	}

	void setSwitchManager(ISwitchManager s) {
		logger.debug("SwitchManager set");
		this.switchManager = s;
	}

	void unsetSwitchManager(ISwitchManager s) {
		if (this.switchManager == s) {
			logger.debug("SwitchManager removed!");
			this.switchManager = null;
		}
	}


    private void floodPacket(RawPacket inPkt) {
        NodeConnector incoming_connector = inPkt.getIncomingNodeConnector();
        Node incoming_node = incoming_connector.getNode();
        
        logger.warn("Nodeconnector toString: {}", incoming_connector.hashCode());
        logger.warn("Node toString: {}", incoming_node.hashCode());
        


        Set<NodeConnector> nodeConnectors =
                this.switchManager.getUpNodeConnectors(incoming_node);

        for (NodeConnector p : nodeConnectors) {
            if (!p.equals(incoming_connector)) {
                try {
                    RawPacket destPkt = new RawPacket(inPkt);
                    destPkt.setOutgoingNodeConnector(p);
                    this.dataPacketService.transmitDataPacket(destPkt);
                } catch (ConstructionException e2) {
                    continue;
                }
            }
        }
    }



    @Override
    public PacketResult receiveDataPacket(RawPacket inPkt) {
        if (inPkt == null) {
            return PacketResult.IGNORED;
        }

        NodeConnector incoming_connector = inPkt.getIncomingNodeConnector();

        // Hub implementation
        if (function.equals("hub")) {
            floodPacket(inPkt);
        } else {
            Packet formattedPak = this.dataPacketService.decodeDataPacket(inPkt);
            if (!(formattedPak instanceof Ethernet)) {
                return PacketResult.IGNORED;
            }

            learnSourceMAC(formattedPak, incoming_connector);
            NodeConnector outgoing_connector = 
                knowDestinationMAC(formattedPak);
            if (outgoing_connector == null) {
                floodPacket(inPkt);
            } else {
                if (!programFlow(formattedPak, incoming_connector,
                            outgoing_connector)) {
                    return PacketResult.IGNORED;
                }
            }
        }
        return PacketResult.CONSUME;
    }

    private void learnSourceMAC(Packet formattedPak, NodeConnector incoming_connector) {
        byte[] srcMAC = ((Ethernet)formattedPak).getSourceMACAddress();
        long srcMAC_val = BitBufferHelper.toNumber(srcMAC);
        this.mac_to_port.put(srcMAC_val, incoming_connector);
    }

    private NodeConnector knowDestinationMAC(Packet formattedPak) {
        byte[] dstMAC = ((Ethernet)formattedPak).getDestinationMACAddress();
        long dstMAC_val = BitBufferHelper.toNumber(dstMAC);
        return this.mac_to_port.get(dstMAC_val) ;
    }

    private boolean programFlow(Packet formattedPak, 
            NodeConnector incoming_connector, 
            NodeConnector outgoing_connector) {
        byte[] dstMAC = ((Ethernet)formattedPak).getDestinationMACAddress();

        Match match = new Match();
        match.setField( new MatchField(MatchType.IN_PORT, incoming_connector) );
        match.setField( new MatchField(MatchType.DL_DST, dstMAC.clone()) );

        List<Action> actions = new ArrayList<Action>();
        actions.add(new Output(outgoing_connector));

        Flow f = new Flow(match, actions);
        f.setIdleTimeout((short)5);

        // Modify the flow on the network node
        Node incoming_node = incoming_connector.getNode();
        Status status = programmer.addFlow(incoming_node, f);

        if (!status.isSuccess()) {
            logger.warn("SDN Plugin failed to program the flow: {}. The failure is: {}",
                    f, status.getDescription());
            return false;
        } else {
            return true;
        }
    }
   
     @Override
    public UUID createData(LearningSwitchData datum) {
        UUID uuid = UUID.randomUUID();
        LearningSwitchData sData = new LearningSwitchData(uuid.toString(), datum.getFoo(), datum.getBar());
        data.put(uuid, sData);
        return uuid;
    }
    @Override
    public LearningSwitchData readData(UUID uuid) {
        return data.get(uuid);
    }
    @Override
    public Map<UUID, LearningSwitchData> readData() {
        return data;
    }
    @Override
    public Status updateData(UUID uuid, LearningSwitchData datum) {
        data.put(uuid, datum);
        return new Status(StatusCode.SUCCESS);
    }
    @Override
    public Status deleteData(UUID uuid) {
        data.remove(uuid);
        return new Status(StatusCode.SUCCESS);
    }
    void init() {
        logger.info("Initializing Simple application");
        data = new ConcurrentHashMap<UUID, LearningSwitchData>();
    }
    void start() {
        logger.info("Simple application starting");
    }

    void stop() {
        logger.info("Simple application stopping");
    }
}
