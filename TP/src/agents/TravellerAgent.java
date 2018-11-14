package agents;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.OptionalDouble;
import java.util.stream.Stream;

import comportements.Ask4Catalog;
import data.ComposedJourney;
import data.Journey;
import data.JourneysList;
import gui.TravellerGui;
import jade.core.AID;
import jade.core.ServiceException;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.SequentialBehaviour;
import jade.core.behaviours.WakerBehaviour;
import jade.core.messaging.TopicManagementHelper;
import jade.gui.GuiAgent;
import jade.gui.GuiEvent;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

/**
 * Journey searcher
 * 
 * @author Emmanuel ADAM
 */
@SuppressWarnings("serial")
public class TravellerAgent extends GuiAgent {
	/** code pour ajout de livre par la gui */
	public static final int EXIT = 0;
	/** code pour achat de livre par la gui */
	public static final int BUY_TRAVEL = 1;

	/** liste des vendeurs */
	protected AID[] vendeurs;

	/**
	 * preference between journeys -, cost, co2, duration or confort ("-" = cost
	 * by defaul)}
	 */
	private String sortMode;

	/** catalog received by the sellers */
	protected JourneysList catalogs;

	/** topic from which the alert will be received */
	AID topic;

	/** gui */
	private TravellerGui window;

	/** Initialisation de l'agent */
	@Override
	protected void setup() {
		this.window = new TravellerGui(this);
		window.setColor(Color.cyan);
		window.println("Hello! AgentAcheteurCN " + this.getLocalName() + " est pret. ");
		window.setVisible(true);

		TopicManagementHelper topicHelper = null;
		try {
			topicHelper = (TopicManagementHelper) getHelper(TopicManagementHelper.SERVICE_NAME);
			topic = topicHelper.createTopic("TRAFFIC NEWS");
			topicHelper.register(topic);
		} catch (ServiceException e) {
			e.printStackTrace();
		}

		addBehaviour(new CyclicBehaviour() {
			@Override
			public void action() {
				ACLMessage msg = myAgent.receive(MessageTemplate.MatchTopic(topic));
				if (msg != null) {
					println("Message recu sur le topic " + topic.getLocalName() + ". Contenu " + msg.getContent()
							+ " Emis par " + msg.getSender().getLocalName());
				} else
					block();
			}

		});
	}

	// 'Nettoyage' de l'agent
	@Override
	protected void takeDown() {
		window.println("Je quitte la plateforme. ");
	}

	///// SETTERS AND GETTERS
	/**
	 * @return agent gui
	 */
	public TravellerGui getWindow() {
		return window;
	}

	/**
	 * try to find a journey : create a sequential behaviour with 3
	 * subbehaviours : search sellers, ask for catalogs, find if the journey is
	 * possible
	 * 
	 * @param from
	 *            origin
	 * @param to
	 *            arrival
	 * @param departure
	 *            date of departure
	 * @param preference
	 *            choose the best (in cost, co2, confort, ...)
	 */
	private void look4Journey(final String from, final String to, final int departure, final String preference) {

		sortMode = preference;
		window.println("recherche de voyage de " + from + " vers " + to + " A  partir de " + departure);

		final SequentialBehaviour seqB = new SequentialBehaviour(this);
		seqB.addSubBehaviour(new OneShotBehaviour(this) {
			/** ask the DFAgent for agents that are in the travel agency */
			@Override
			public void action() {
				vendeurs = AgentToolsEA.searchAgents(myAgent, "travel agency", null);
			}
		});
		seqB.addSubBehaviour(new OneShotBehaviour(this) {
			/** add a behaviour to ask a catalog of journeys to the sellers */
			@Override
			public void action() {
				myAgent.addBehaviour(new Ask4Catalog(myAgent, new ACLMessage(ACLMessage.INFORM)));
			}
		});
		seqB.addSubBehaviour(new WakerBehaviour(this, 100) {
			/**
			 * display the merged catalog and try to find the best journey that
			 * corresponds to the data sent by the gui
			 */
			@Override
			protected void onWake() {

				if (catalogs != null) {
					// println("here is my catalog : ");
					// println(" -> " + catalogs);
					computeComposedJourney(from, to, departure, preference);

				}
				if (catalogs == null) {
					println("I have no catalog !!! ");
				}
			}
		});

		addBehaviour(seqB);

	}

	private void computeComposedJourney(final String from, final String to, final int departure,
			final String preference) {
		final List<ComposedJourney> journeys = new ArrayList<>();
		final boolean result = catalogs.findIndirectJourney(from, to, departure, 120, new ArrayList<Journey>(),
				new ArrayList<String>(), journeys);

		if (!result) {
			println("no journey found !!!");
		}
		if (result) {
			for(ComposedJourney c : journeys){
				window.println(c.toString());
			}
			
			
			
		}
	}

	/** a comparator based equitably on the cost and the confort */
	private int sortbyCostAndConfort(ComposedJourney journeyToSort, ComposedJourney otherJourney) {
		double jCost = journeyToSort.getCost();
		double otherCost = otherJourney.getCost();
		double maxCost = Math.max(jCost, otherCost);
		double delatCost = (maxCost == 0 ? 0 : (jCost - otherCost) / maxCost);
		int jConfort = journeyToSort.getConfort();
		int otherConfort = otherJourney.getConfort();
		int maxConfort = Math.max(jConfort, otherConfort);
		int deltaConfort = (maxConfort == 0 ? 0 : (otherConfort - jConfort) / maxConfort);
		return (int) (delatCost + deltaConfort);
	}

	/** get event from the GUI */
	@Override
	protected void onGuiEvent(final GuiEvent eventFromGui) {
		if (eventFromGui.getType() == TravellerAgent.EXIT) {
			doDelete();
		}
		if (eventFromGui.getType() == TravellerAgent.BUY_TRAVEL) {
			look4Journey((String) eventFromGui.getParameter(0), (String) eventFromGui.getParameter(1),
			(Integer) eventFromGui.getParameter(2), (String) eventFromGui.getParameter(3));
		}
	}

	/**
	 * @return the vendeurs
	 */
	public AID[] getVendeurs() {
		return vendeurs.clone();
	}

	/**
	 * @param vendeurs
	 *            the vendeurs to set
	 */
	public void setVendeurs(final AID... vendeurs) {
		this.vendeurs = vendeurs;
	}

	/** -, cost, co2, duration or confort */
	public String getSortMode() {
		return sortMode;
	}

	/**
	 * print a message on the window lined to the agent
	 * 
	 * @param msg
	 *            text to display in th window
	 */
	public void println(final String msg) {
		window.println(msg);
	}

	/** @return the list of journeys */
	public JourneysList getCatalogs() {
		return catalogs;
	}

	/** set the list of journeys */
	public void setCatalogs(final JourneysList catalogs) {
		this.catalogs = catalogs;
	}

}
