package SimulationLibrary;

/**
 *    
 * @author Min Yung M�nnig
 *
 */

public abstract class Relation extends UniSimObject implements Comparable<Relation> {
	
	private int id;
	private double value = 0.0;
	private String type = "";
	private Concept source;
	private Concept target;
	
	// calculationSource is used by calculate-method. For example Multiply-Relation needs to know
	// "what" shall be multiplied by <value>
	private Concept calculationSource;
		
	// dynamic = true allows to create a dynamic relation with a changing value.
	// the value will be overwritten with the calculationSource-value before calculating.
	private Concept dynamicValueSource = null;
	
	// There can be a Concept-ArrayList. The indices are used to know what Concept in the List shall be used. 
	private int sourceIndex = 0; 
	private int targetIndex = 0;
	
	// Documentation: see Problem 1
	private double interim = 0.0;
	private boolean move = true;
	
	private boolean relCountReset = false;	// if true, this relation will reset the relCount-property of the target
	private boolean negThresholdReset = false;	// the reset is started in the method addInterim
	private boolean posThresholdReset = false;
	
	public Relation() { }
	
	public Relation(Concept source, Concept target) {
		this.source = source;
		this.target = target;
		this.calculationSource = source;
		this.dynamicThresholdSource = null;
	}
	
	public Concept getSource() {
		synchronized(this) {
			return source;
		}
	}
	
	public void setSource(Concept s) {
		synchronized(this) {
			source = s;
		}
	}
	
	public Concept getTarget() {
		synchronized(this) {
			return target;
		}
	}

	public void setTarget(Concept t) {
		synchronized(this) {
			target = t;
		}
	}
	
	public double getInterim() {
		synchronized(this) {
			return interim;
		}
	}
	
	public void setInterim(double i) {
		synchronized(this) {
			interim = i;
		}
	}
	
	public double getValue() {
		synchronized(this) {
			return value;
		}
	}
	
	public void setValue(double v) {
		synchronized(this) {
			value = v;
		}
	}
	
	public Concept getCalculationSource() {
		synchronized(this) {
			return calculationSource;
		}
	}
	
	public void setCalculationSource(Concept c) {
		synchronized(this) {
			calculationSource = c;
		}
	}
	
	public int getSourceIndex() {
		synchronized(this) {
			return sourceIndex;
		}
	}
	
	public int getTargetIndex() {
		synchronized(this) {
			return targetIndex;
		}
	}
	
	public int getId() {
		return id;
	}
	
	public void setId(int id) {
		synchronized(this) {
			this.id = id;
		}
	}
	
	public boolean hasDynamicValue() {
		synchronized(this) {
			if (dynamicValueSource != null) { return true; }
			else { return false; }
		}
	}
		
	public Concept getDynamicValueSource() {
		synchronized(this) {
			return dynamicValueSource;
		}
	}
	
	public void setDynamicValueSource(Concept c) {
		synchronized(this) {
			dynamicValueSource = c;
		}
	}
	
	public boolean shallMove() {
		synchronized(this) {
			if (move) { return true; }
			else { return false; }
		}
	}
	
	public void shallMove(boolean m) {
		synchronized(this) {
			move = m;
		}
	}
	
	public void switchMove() {
		synchronized(this) {
			if (shallMove()) { shallMove(false); }
			else { shallMove(true); }
		}
	}
	
	public String getType() {
		return type;
	}
		
	/**
	 * The type is set by the subclasses and can not be changed manually.
	 * @param t
	 */
	protected void setType(String t) {
		if (type.equals("")) { type = t; }
	}
	
	public void switchRelCountReset() {
		synchronized(this) {
			if (relCountReset) { useRelCountReset(false); }
			else { useRelCountReset(true); }
		}
	}
	
	public boolean useRelCountReset() {
		synchronized(this) {
			return relCountReset;
		}
	}
	
	public void useRelCountReset(boolean res) {
		synchronized(this) {
			relCountReset = res;
		}
	}
	
	public void switchNegThresholdReset() {
		synchronized(this) {
			if (negThresholdReset) { useNegThresholdReset(false); }
			else { useNegThresholdReset(true); }
		}
	}
	
	public boolean useNegThresholdReset() {
		synchronized(this) {
			return negThresholdReset;
		}
	}
	
	public void useNegThresholdReset(boolean res) {
		synchronized(this) {
			negThresholdReset = res;
		}
	}
	
	public void switchPosThresholdReset() {
		synchronized(this) {
			if (posThresholdReset) { usePosThresholdReset(false); }
			else { usePosThresholdReset(true); }
		}
	}
	
	public boolean usePosThresholdReset() {
		synchronized(this) {
			return posThresholdReset;
		}
	}
	
	public void usePosThresholdReset(boolean res) {
		synchronized(this) {
			posThresholdReset = res;
		}
	}
	
	/**
	 * the subclasses calculate the interim
	 */
	public void calculate() {
		if (dynamicValueSource != null) {
			value = dynamicValueSource.getValue();
		}
	}
	
	/**
	 * the interim is added to the target, and in case of move=true substracting from the source
	 * this methods also starts to raise the threshold-values
	 * and in case resetting the target thresholds
	 */
	public void addInterim() {
		target.setValue(target.getValue() + interim);	// add interim to the target
		if (move) {	// remove interim from the source if move = true
			source.setValue(source.getValue() + (interim * -1));
		}
		addTargetThresholdValues();												// add the changes to the thresholds
		if (relCountReset) target.resetThresholdRelationCount();
		if (negThresholdReset) target.resetThresholdNegativeValueCount();
		if (posThresholdReset) target.resetThresholdPositiveValueCount();
	}
	
	/**
	 * adding the stored interim-value to the threshold-counters of target-concept
	 */
	public void addTargetThresholdValues() {
		if (target.isThresholdAlwaysNull()) {
			// the target-concept can block raising it's counters
			return;
		}
		if (interim > 0) {
			target.raiseThresholdPositiveValueCount(interim);
		}
		else if (interim < 0) {
			target.raiseThresholdNegativeValueCount(interim);
		}
		target.raiseThresholdRelationCount();
	}
	
	/**
	 * checks if the relation meets the threshold-requirements to be active
	 * the relation threshold is compared with threshold-counters of the source-concept
	 * @return
	 */
	public boolean isActive() {
		double thresholdCount = 0.0;
		if (relationCount) { thresholdCount = source.getThresholdRelationCount(); }
		else if (positiveThreshold) { thresholdCount =  source.getThresholdPositiveValueCount(); }
		else if (negativeThreshold) { thresholdCount =  source.getThresholdNegativeValueCount(); }
		return super.isActive(thresholdCount);
	}
		
	public String toCommand() {
		String s = "nr " + source.getName() + " " + target.getName();
		if (type.equals("+")) s += " +";
		else if (type.equals("-")) s += " -";
		else if (type.equals("/")) s += " /";
		else if (type.equals("*")) s += " *";
		else if (type.equals("%")) s += " %";
		if (value != 0) s += " v:" + value;
		s += " cs:" + calculationSource.getName();
		if (dynamicValueSource != null) s += " dvs:" + dynamicValueSource.getName();
		if (move) s += " move:true";
		else s += " move:false";
		if (threshold != 0) s += " thr:" + threshold;
		if (relationCount) s += " rct";
		else s += " rcf";
		if (positiveThreshold) s += " ptt";
		else s += " ptf";
		if (negativeThreshold) s += " ntt";
		else s += " ntf";
		if (valueThreshold) s += " vtt";
		else s += " vtf";
		if (dynamicThresholdSource != null) s += " dts:" + dynamicThresholdSource.getName();
		s += " activeCondition:" + activeCondition;
		if (relCountReset) s += " rcrt";
		if (negThresholdReset) s += " ntrt";
		if (posThresholdReset) s += " ptrt";
		return s;
	}
	
	public String toShortString() {
		String s = id + ": " + getSource().getName() + " -> " + getTarget().getName();
		if (type.equals("+")) { s += " (+)"; }
		else if (type.equals("/")) { s += " (/)"; }
		else if (type.equals("-")) { s += " (-)"; }
		else if (type.equals("*")) { s += " (*)"; }
		else if (type.equals("%")) { s += " (%)"; }
		s += " v:" + value;
		if (dynamicValueSource != null) { s += " dvs:" + dynamicValueSource.getName(); }
		if (move) { s += " mov"; }
		// TODO threshold-outputs not optimal
		if (threshold != 0.0) {
			if (relationCount) { s += " relCount"; }
			if (positiveThreshold) { s += " posThr"; }
			if (negativeThreshold) { s += " negThr"; }
			if (valueThreshold) { s += " valThr"; }
			s += " " + activeCondition;
			s += " thr:" + threshold;
		}
		if (relCountReset) s += " rcrt";
		if (negThresholdReset) s += " ntrt";
		if (posThresholdReset) s += " ptrt";
		return s;
	}
	
	@Override
	public String toString() {
		synchronized(this) {
			String s = "Relation: " + getSource().getName() + " -> " + getTarget().getName() + "\n";
			s += "  type: " + type + "\n";
			s += "  ID: " + id + "\n";
			s += "  calculation-Source: " + calculationSource.getName() + "\n";
			s += "  value: " + value + "\n";
			s += "  dynamicValueSource: ";
			if (dynamicValueSource != null) s += dynamicValueSource.getName() + "\n";
			else s += "null\n";
			s += "  move: ";
			if (move) { s += "true\n"; }
			else { s += "false\n"; }
			s += "\n";
			s += super.toString();
			s += "  Target-Threshold-Reset:";
			s += "    relCountReset: ";
			if (relCountReset) s += "true\n";
			else s += "false\n";
			s += "    negThresholdReset: ";
			if (negThresholdReset) s += "true\n";
			else s += "false\n";
			s += "    posThresholdReset: ";
			if (posThresholdReset) s += "true\n";
			else s += "false\n";
			return s;
		}
	}
	
	@Override
	public int compareTo(Relation r) {
		return id - r.id;
	}
	
	
}
