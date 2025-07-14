package SimulationLibrary;

import java.util.ArrayList;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;

import Library.AvlTree;

public class Concept extends UniSimObject implements Comparable<Concept> {
	private String name = "";
	private double value = 0.0;
	private double startValue = 0.0;
	private AvlTree<Relation> relations = new AvlTree<Relation>();
	private LinkedBlockingDeque<Integer> nextFreeRelationId = new LinkedBlockingDeque<Integer>();
	private int relationIdCounter = 1; // the highest id-value that has been added to the queue up to now
	private boolean startingConcept = false; // entry-point of graph/matrix
	private boolean stoppingConcept = false; // if this is true and this concept is taken from the workingQueue, the 
												// matrix will get the stop-command
	
	private boolean repetitive = false; // if true then the same concept can be in working queue several times

	private int processedCounter = 0;	// how many times this concept has been fetched from the workingQueue and processed
	
	private String workingQueuePriority = "low"; // "high" -> high priority or "low" -> low priority
	
	private boolean queueWhenZero = false; // says if the concept comes into workingQueue although the changing-value was 0 
	
	// input-concept
	private boolean input = false;	// marks an input-concept that will be added to the workingQueue when its value is changed by user
	
	//passive Threshold-Counters
	private boolean thresholdResetAfterOneRelation = false; // if true, then after one Relation becomes active, the thresholds will be set back to 0
	private boolean thresholdAlwaysNull = false;	// if true all Threshold-Counters staying on 0.0
	private int countActiveRelations = 0;	// counts the number of relations that reached the threshold, is raised by worker
	private int maxIncomingRelations = 0;	// the number of all relations that have this concept as target
	private int thresholdRelationCount = 0;
	private double thresholdNegativeValueCount = 0.0;
	private double thresholdPositiveValueCount = 0.0;
		
	public Concept() {
		try {
			nextFreeRelationId.put(1);
		} catch (InterruptedException e) {
			print(" -> Problems in Concept while adding a relation-id to the queue.");
		}
	}
	
	public Concept(String name) {
		this();
		this.name = name;
	}
	
	public void reset() {
		synchronized(this) {
			value = startValue;
			processedCounter = 0;
			resetThreshold();
		}
	}
	
	public void resetThreshold() {
		synchronized(this) {
			countActiveRelations = 0;
			resetThresholdRelationCount();
			resetThresholdNegativeValueCount();
			resetThresholdPositiveValueCount();
		}
	}
	
	public void resetThresholdRelationCount() {
		synchronized(this) {
			thresholdRelationCount = 0;
		}
	}
	
	public void resetThresholdNegativeValueCount() {
		synchronized(this) {
			thresholdNegativeValueCount = 0;
		}
	}
	
	public void resetThresholdPositiveValueCount() {
		synchronized(this) {
			thresholdPositiveValueCount = 0;
		}
	}
	
	public String getName() {
		synchronized(this) {
			return this.name;
		}
	}
	
	public void setName(String n) {
		synchronized(this) {
			this.name = n;
		}
	}
	
	public double getValue() {
		synchronized(this) {
			return value;
		}
	}
	
	public void setValue(double value) {
		synchronized(this) {
			this.value = value;
		}
	}
	
	public double getStartValue() {
		synchronized(this) {
			return this.startValue;
		}
	}
	
	public void setStartValue(double value) {
		synchronized(this) {
			this.startValue = value;
		}
	}
	
	/**
	 * Change the bool-value of startingConcept.
	 * @return
	 */
	public void switchStartConcept() {
		synchronized(this) {
			if (isStartConcept()) { isStartConcept(false); }
			else { isStartConcept(true); }
		}
	}
	
	public boolean isStartConcept() {
		synchronized(this) {
			if (startingConcept) { return true; }
			else { return false; }
		}
	}
	
	public void isStartConcept(boolean start) {
		synchronized(this) {
			startingConcept = start;
		}
	}
	
	/**
	 * Change the bool-value of startingConcept.
	 * @return
	 */
	public void switchStopConcept() {
		synchronized(this) {
			if (isStopConcept()) { isStopConcept(false); }
			else { isStopConcept(true); }
		}
	}
	
	public boolean isStopConcept() {
		synchronized(this) {
			if (stoppingConcept) { return true; }
			else { return false; }
		}
	}
	
	public void isStopConcept(boolean stop) {
		synchronized(this) {
			stoppingConcept = stop;
		}
	}
	
	/**
	 * checks if the concept meets the treshold-requirements to be active
	 * the threshold is compared with the own threshold-counters of this object
	 * @return
	 */
	public boolean isActive() {
		double thresholdCount = 0.0;
		if (relationCount) { thresholdCount = getThresholdRelationCount(); }
		else if (positiveThreshold) { thresholdCount = getThresholdPositiveValueCount(); }
		else if (negativeThreshold) { thresholdCount = getThresholdNegativeValueCount(); }
		else if (valueThreshold)  { thresholdCount = getThresholdValueCount(); }
		return super.isActive(thresholdCount);
	}
		
	public boolean isRepetitive() {
		synchronized(this) {
			if (repetitive) { return true; }
			else { return false; }
		}
	}
	
	public void isRepetitive(boolean rep) {
		synchronized(this) {
			repetitive = rep;
		}
	}
	
	/**
	 * change the bool-value of thresholdAlwaysNull
	 */
	public void switchThresholdAlwaysNull() {
		synchronized(this) {
			if (thresholdAlwaysNull) thresholdAlwaysNull = false;
			else thresholdAlwaysNull = true;
		}
	}

	public boolean isThresholdAlwaysNull() {
		synchronized(this) {
			return thresholdAlwaysNull;
		}
	}
	
	public void isThresholdAlwaysNull(boolean an) {
		synchronized(this) {
			thresholdAlwaysNull = an;
		}
	}
	
	/**
	 * change the bool-value of repetitive
	 */
	public void switchRepetitive() {
		synchronized(this) {
			if (repetitive) { repetitive = false; }
			else { repetitive = true; }
		}
	}
	
	public boolean isResetOne() {
		return thresholdResetAfterOneRelation;
	}
	
	public void isResetOne(boolean reset) {
		synchronized(this) {
			thresholdResetAfterOneRelation = reset;
		}
	}
	
	public void switchResetOne() {
		synchronized(this) {
			if (thresholdResetAfterOneRelation) { isResetOne(false); }
			else { isResetOne(true); }
		}
	}
	
	/**
	 * set all threshold-reset-flags to false	
	 */
	public void switchResetNone() {
		synchronized(this) {
			isResetOne(false);
		}
	}
	
	public boolean comesIntoQueueWhenZero() {
		synchronized(this) {
			return queueWhenZero;
		}
	}
	
	public void comesIntoQueueWhenZero(boolean into) {
		synchronized(this) {
			queueWhenZero = into;
		}
	}
	
	public void switchComesIntoQueueWhenZero() {
		synchronized(this) {
			if (queueWhenZero) { comesIntoQueueWhenZero(false); }
			else { comesIntoQueueWhenZero(true); }
		}
	}
	
	public int getNumberActiveRelations() {
		synchronized(this) {
			return countActiveRelations;
		}
	}
	
	public void raiseNumberActiveRelations() {
		synchronized(this) {
			countActiveRelations++;
		}
	}
	
	public int getMaxIncomingRelations() {
		synchronized(this) {
			return maxIncomingRelations;
		}
	}
	
	public void raiseMaxIncomingRelations() {
		synchronized(this) {
			maxIncomingRelations++;
		}
	}
	
	public int getThresholdRelationCount() {
		synchronized(this) {
			return thresholdRelationCount;
		}
	}
	
	public void raiseThresholdRelationCount() {
		synchronized (this) {
			thresholdRelationCount++;
		}
	}
	
	public double getThresholdPositiveValueCount() {
		synchronized(this) {
			return thresholdPositiveValueCount;
		}
	}
	
	public void raiseThresholdPositiveValueCount(double val) {
		synchronized (this) {
			thresholdPositiveValueCount += val;
		}
	}
	
	public double getThresholdNegativeValueCount() {
		synchronized(this) {
			return thresholdNegativeValueCount;
		}
	}
	
	public void raiseThresholdNegativeValueCount(double val) {
		synchronized (this) {
			thresholdNegativeValueCount += val;
		}
	}
	
	public double getThresholdValueCount() {
		synchronized(this) {
			return thresholdPositiveValueCount + thresholdNegativeValueCount;
		}
	}
	
	public int getProcessedCounter() {
		synchronized(this) {
			return processedCounter;
		}
	}
	
	public void raiseProcessedCounter() {
		synchronized(this) {
			processedCounter++;
		}
	}
	
	public boolean isInput() {
		synchronized(this) {
			if (input) { return true; }
			else { return false; }
		}
	}
	
	public void isInput(boolean in) {
		synchronized(this) {
			input = in;
		}
	}
	
	public void switchInput() {
		synchronized(this) {
			if (input) { isInput(false); }
			else { isInput(true); }
		}
	}
	
	public String getWorkingQueuePriority() {
		synchronized(this) {
			return workingQueuePriority;
		}
	}
	
	public void setWorkingQueuePriority(String prio) {
		synchronized(this) {
			if (prio.equals("high")) {
				workingQueuePriority = prio;
			}
			else if (prio.equals("low")) {
				workingQueuePriority = prio;
			}
			// other priorities will not be processed.
		}
	}
	
	public void switchWorkingQueuePriority() {
		synchronized(this) {
			if (workingQueuePriority.equals("low")) {
				setWorkingQueuePriority("high");
			}
			else {
				setWorkingQueuePriority("low");
			}
		}
	}
	
	public int addRelation(Relation r) {
		synchronized(this) {
			Integer relid = null;
			while (relid == null) {
				try {
					relid = nextFreeRelationId.poll();
				}
				catch (Exception e) { }
			}
			if (nextFreeRelationId.isEmpty()) {
				relationIdCounter++;
				try {
					nextFreeRelationId.put(relationIdCounter);
				} catch (InterruptedException e) { }
			}
			r.setId(relid);
			relations.insert(r);
			return relid;
		}
	}
		
	/**
	 * removes a relation with the given id from the relations-tree
	 * @param id
	 * @return
	 */
	public boolean removeRelation(int id) {
		// create a relation with the given id, to find it in the tree
		Concept c = new Concept("temp");
		Relation r = new RelationAdd(c, c);
		r.setId(id);
		// remove the relation
		Relation found = relations.delete(r);
		// return a bool-value and update the nextFreeRelationId
		if (found == null) return false;
		else nextFreeRelationId.addFirst(id);
		return true;
	}
	
	public AvlTree<Relation> getRelations() {
		synchronized(this) {
			return relations;
		}
	}
	
	public LinkedBlockingQueue<Relation> getRelationsLBQ() {
		synchronized(this) {
			return this.relations.traverseInOrderLBQ();
		}
	}
	
	public String toCommand() {
		String s = "nc " + name;
		if (getStartValue() != 0) s += " sv:" + getStartValue();
		s += " start:" + startingConcept;
		s += " rep:" + repetitive;
		s += " pri:" + workingQueuePriority;
		s += " zero:" + queueWhenZero;
		s += " inp:" + input;
		s += " resetOne:" + thresholdResetAfterOneRelation;
		s += " thrCount:" + thresholdAlwaysNull;
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
		
		return s;
	}
	
	public static void print(String text) {
		System.out.println(text);
	}
	
	public String toShortString() {
		String s = name;
		s += "  " + this.getValue() + " (long:" + ((long)this.getValue()) + ")";
		if (startingConcept) s += " start";
		if (input) s += " input";
		if (workingQueuePriority.equals("high")) { s += " prio:high"; }
		if (queueWhenZero) s += " zero";
		return s;
	}
	
	@Override
	public String toString() {
		String s = "--------------------\n";
		s += "Concept: " + name + "\n";
		s += "  value: " + this.getValue() + "\n";
		s += "  start-value: " + this.getStartValue() + "\n";
		s += "  start-concept: " + startingConcept + "\n";
		s += "  repetitive: " + repetitive + "\n";
		s += "  input: " + input + "\n";
		s += "  threshold reset after one relation: " + thresholdResetAfterOneRelation + "\n";
		s += "  workingQueue-priority: " + workingQueuePriority + "\n";
		s += "  comes into queue although change was 0: " + queueWhenZero + "\n";
		s += "\n";
		s += super.toString();
		s += "\n";
		s += "  Threshold-Counters:\n";
		s += "    maxIncomingRelations: " + maxIncomingRelations + "\n";
		s += "    entered Relations: " + thresholdRelationCount + "\n";
		s += "    sum of positive Values: " + getThresholdPositiveValueCount() + "\n";
		s += "    sum of negative Values: " + getThresholdNegativeValueCount() + "\n";
		s += "    sum of neg+pos Values: " + getThresholdValueCount() + "\n";
		s += "\n";
		s += "  Relations:\n";
		for (Relation r: relations.traverseInOrder()) {
			s += "    -> " + r.getTarget().name + "\n";
		}
		s += "\n";
		s += "  times processed: " + processedCounter + "\n";
		return s;
	}
	
	@Override
	public int compareTo(Concept c) {
		return name.compareTo(c.name);
	}
}
