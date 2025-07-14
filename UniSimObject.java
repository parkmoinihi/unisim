package SimulationLibrary;

import java.util.LinkedList;


public abstract class UniSimObject {
	private double value = 0.0;
	
	// Threshold
	protected double threshold = 0.0;	// threshold = 0.0 and activeCondition >= means this object is always active
										// except you are using negativeThreshold
	protected boolean relationCount = true; // if true then threshold refers to amount of incoming Concepts/Relations and not Value
	protected boolean negativeThreshold = false;
	protected boolean positiveThreshold = false;
	protected boolean valueThreshold = false;	// threshold refers to the value of source-concept
	protected Concept dynamicThresholdSource = null;	// the concept and its value the threshold of this relation becomes
	protected String activeCondition = ">=";	// relation is active when threshold-counters of concept are >= relation-threshold
									// active can be: <, <=, =, >=, >
	protected LinkedList<String> command = new LinkedList<String>(); // UniSim Objects can hold any unisim-commands, which will be given to the matrix, when the object is active
	protected boolean commandOnce = false;	// flag that shows if the commands shall be repeated (default yes)
	
	public boolean isActive(double thresholdCount) {
		synchronized(this) {
			return innerIsActive(thresholdCount);
		}
	}
	
	/**
	 * checks if the object meets the threshold-requirements to be active
	 * @return
	 */
	protected final boolean innerIsActive(double thresholdCount) {
		if (hasDynamicThreshold()) {
			threshold = dynamicThresholdSource.getValue();
		}
		if (activeCondition.equals("<")) {
			if (thresholdCount < threshold) { return true; }
			else { return false; }
		}
		else if (activeCondition.equals("<=") || activeCondition.equals("=<")) {
			if (thresholdCount <= threshold) { return true; }
			else { return false; }
		}
		else if (activeCondition.equals("=")) {
			if (thresholdCount == threshold) { return true; }
			else { return false; }
		}
		else if (activeCondition.equals(">=") || activeCondition.equals("=>")) {
			if (thresholdCount >= threshold) { return true; }
			else { return false; }
		}
		else if (activeCondition.equals(">")) {
			if (thresholdCount > threshold) { return true; }
			else { return false; }
		}
		return false;
	}
	
	public boolean usePositiveThreshold() {
		synchronized(this) {
			if (positiveThreshold) { return true; }
			else { return false; }
		}
	}
	public void usePositiveThreshold(boolean use) {
		synchronized(this) {
			if (use) {
				relationCount = false;
				negativeThreshold = false;
				valueThreshold = false;
			}
			positiveThreshold = use;
		}
	}
	public void switchPositiveThreshold() {
		synchronized(this) {
			if (usePositiveThreshold()) { usePositiveThreshold(false); }
			else { usePositiveThreshold(true); }
		}
	}
	public boolean useNegativeThreshold() {
		synchronized(this) {
			if (negativeThreshold) { return true; }
			else { return false; }
		}
	}
	public void useNegativeThreshold(boolean use) {
		synchronized(this) {
			if (use) {
				relationCount = false;
				positiveThreshold = false;
				valueThreshold = false;
			}
			negativeThreshold = use;
		}
	}
	public void switchNegativeThreshold() {
		synchronized(this) {
			if (useNegativeThreshold()) { useNegativeThreshold(false); }
			else { useNegativeThreshold(true); }
		}
	}
	public boolean useValueThreshold() {
		synchronized(this) {
			if (valueThreshold) { return true; }
			else { return false; }
		}
	}
	public void useValueThreshold(boolean use) {
		synchronized(this) {
			if (use) {
				relationCount = false;
				negativeThreshold = false;
				positiveThreshold = false;
			}
			valueThreshold = use;
		}
	}
	public void switchValueThreshold() {
		synchronized(this) {
			if (useValueThreshold()) { useValueThreshold(false); }
			else { useValueThreshold(true); }
		}
	}
	public double getThresholdValue() {
		synchronized(this) {
			return threshold;
		}
	}
	public void setThresholdValue(double v) {
		synchronized(this) {
			threshold = v;
		}
	}
	public boolean hasDynamicThreshold() {
		synchronized(this) {
			if (dynamicThresholdSource != null) { return true; }
			else { return false; }
		}
	}
	
	public Concept getDynamicThresholdSource() {
		synchronized(this) {
			return dynamicThresholdSource;
		}
	}
	
	public void setDynamicThresholdSource(Concept c) {
		synchronized(this) {
			dynamicThresholdSource = c;
		}
	}
	
	public String getActiveCondition() {
		synchronized(this) {
			return activeCondition;
		}
	}
	
	public void setActiveCondition(String act) {
		synchronized(this) {
			if (act.equals("<") || act.equals("<=") || act.equals("=<") || act.equals("=") || act.equals(">=") || act.equals("=>") || act.equals(">")) {
				activeCondition = act;
			}
		}
	}

	/**
	 * Is a getter-method of the boolean flag relationCount. relationCount says whether the threshold is compared to
	 * the numbers of relations that reached the concept.
	 * @return
	 */
	public boolean useRelationCount() {
		synchronized(this) {
			if (relationCount) { return true; }
			else { return false; }
		}
	}

	public void useRelationCount(boolean use) {
		synchronized(this) {
			if (use) {
				positiveThreshold = false;
				negativeThreshold = false;
				valueThreshold = false;
			}
			relationCount = use;
		}
	}

	public void switchRelationCount() {
		synchronized(this) {
			if (useRelationCount()) { useRelationCount(false); }
			else { useRelationCount(true); }
		}
	}
	
	public void addCommand(String command) {
		this.command.add(command);
	}
	
	public String toString() {
		synchronized(this) {
			return innerToString();
		}
	}
	
	private final String innerToString() {
		synchronized(this) {
			String s = "  Threshold-Settings:\n";
			s += "    relation count: ";
			if (relationCount) { s += "true\n"; }
			else { s += "false\n"; }
			s += "    positive Threshold: ";
			if (positiveThreshold) { s += "true\n"; }
			else { s += "false\n"; }
			s += "    negative Threshold: ";
			if (negativeThreshold) { s += "true\n"; }
			else { s += "false\n"; }
			s += "    value Threshold: ";
			if (valueThreshold) { s += "true\n"; }
			else { s += "false\n"; }
			s += "    activeCondition: " + activeCondition + "\n";
			s += "    value: " + threshold + "\n";
			return s;
		}
	}
	
}
