/* change history:
last version 26.02.2015 threshold
1. checked toCommand
2. rebuilt toString-structure
3. relation can now cause a threshold-reset, relation-options: relCountReset, negThresholdReset, posThresholdReset
4. added stopwatch from command run until command stop
5. add the flag stoppingConcept to Concept
6. changed concept-options st, sf to startt and startf, added stop, stopt and stopf
7. bugfix dynamic-value-source was null when set, although it exists
*/

package SimulationLibrary;

/**
 * 
 * This is not a classic matrix but more like a graph. It connects Concepts. The connection is called Relation.
 * 
 * @author Min Yung M�nnig
 *
 */

//TODO	working on Concept -> Command
//		adding command print text + ...

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;

import Library.AvlTree;
import Library.Pair;
import Library.Scanner;
import Library.StopwatchNano;

public class Matrix implements Runnable {
	protected AvlTree<Concept> concepts = new AvlTree<Concept>();
	LinkedBlockingQueue<Concept> startConcepts = new LinkedBlockingQueue<Concept>();
	LinkedBlockingDeque<Concept> workingQueue = new LinkedBlockingDeque<Concept>();
	LinkedBlockingDeque<String> commands = new LinkedBlockingDeque<String>();
	
	public boolean processingCommands = false; // flag if matrix is processing commands at the moment
	public boolean running = false;	// flag if matrix is running the simulation at the moment
	
	private ArrayList<Worker> workers = new ArrayList<Worker>();
	private ArrayList<Thread> threads = new ArrayList<Thread>();
		
	private int workerAmount = 0; // if 0 then default software setting: cores - 2
	
	private String workingPath = "";
	
	private StopwatchNano clock = new StopwatchNano();
	
	public boolean protocollText = false;	// if true, then the working on commands will be printed
	
	public Matrix() {
		createWorkers();
		print("UniSim> ");
	}
	
	/**
	 * Creates a new concept with the given name and adds it with default values.
	 * @param name
	 */
	public Concept addConcept(String name) {
		if (isValidConceptName(name)) {
			Concept c = new Concept(name);
			return addConcept(c);
		}
		else {
			println(" -> Concept-name " + name + " is not valid.");
		}
		return null;
	}
	
	/**
	 * If concept-name is already used, the new concept will not be added.
	 * @param c
	 */
	private Concept addConcept(Concept c) {
		if (c == null) {
			println("Matrix.addConcept: Concept is null, Concept not added.");
			return null;
		}
		/*if (findConcept(c) != null) {
			println("Matrix.addConcept: Concept \"" + c.getName() + "\" already exists.");
			return null;
		}*/
		if (!concepts.insert(c)) {
			println("Matrix.addConcept: Concept(\"" + c.getName() + "\") not added, rejected by tree.");
			return null;
		}
		if (c.isStartConcept()) { startConcepts.add(c); }
		return c;
	}
		
	/**
	 * Checks if a given name is a valid concept name.
	 * name may not start with "-", may not have spaces, may not be ""
	 * @param name
	 * @return
	 */
	public boolean isValidConceptName(String name) {
		if (name.equals("")) { return false; }
		if (name.contains(" ")) { return false; }
		if (name.startsWith("-")) { return false; }
		return true;
	}
	
	public Integer addRelation(Relation r) {
		if (r == null) {
			println("Matrix.addRelation: Relation is null, Relation not added.");
		}
		if (r.getSource() == null) {
			println("Matrix.addRelation: Source-Concept is null, Relation not added.");
			return null;
		}
		if (r.getTarget() == null) {
			println("Matrix.addRelation: Target-Concept is null, Relation not added.");
			return null;
		}
		Concept source = concepts.find(r.getSource());
		if ( source == null) {
			println("Matrix.addRelation: Source-Concept(\"" + r.getSource().getName() + "\") not found, relation not added.");
			return null;
		}
		if (concepts.find(r.getTarget()) == null) {
			println("Matrix.addRelation: Target-Concept(\"" + r.getTarget().getName() + "\") not found, relation not added.");
			return null;
		}
		int relid = source.addRelation(r);
		r.getTarget().raiseMaxIncomingRelations();
		protocoll(" -> (" + r.getType() + ")Relation added to " + r.getSource().getName());
		return relid;
	}

	/**
	 * Checks if the current concept must be in the startConcepts-List or not and
	 * in case change it.
	 */
	private void checkStartConceptsList(Concept c) {
		if (c == null) { return; }
		if (c.isStartConcept()) {	// current Concept must be in the list
			if (!startConcepts.contains(c)) {
				startConcepts.add(c);
			}
		}
		else {									// current Concept must NOT be in the list
			if (startConcepts.contains(c)) {
				startConcepts.remove(c);
			}
		}
	}
	
	/**
	 * Takes a string and removes all parts beginning with double-slash //
	 * @param text
	 * @return
	 */
	private String removeComments(String text) {
		int commandEnd = text.indexOf("//");
		if (commandEnd > -1) { // comment found
			text = text.substring(0, commandEnd);
		}
		return text;
	}
	
	public void command(String text) throws IOException {
		text = removeComments(text);
		// println(" -> removing comments: " + text);
		Scanner scanner = new Scanner();
		scanner.addSeparator("\"", "\"");
		scanner.scan(text);
		LinkedList<String> words = scanner.words;
		// println("given command: " + text);
		if (words.isEmpty()) {
			return;
		}
		else {	// there are words
			String word = words.poll();
			if (word.equalsIgnoreCase("run")) { // starts the matrix from the very beginning 
				commandRun();
				return;
			}
			else if (word.equalsIgnoreCase("stop")) { // will cause the workers to stop their work after finishing the actual concept
				commandStop();
				return;
			}
			else if (word.equalsIgnoreCase("proceed")) { // will cause the workers to go on working without resetting everything
				commandProceed();
				return;
			}
			else if (word.equalsIgnoreCase("reset")) { // will cause the workers to go on working without resetting everything
				commandReset();
				return;
			}
			else if (word.equalsIgnoreCase("exit")) {
				setWorkerOrder("exit");
				return;
			}
			else if (word.equalsIgnoreCase("print") || word.equalsIgnoreCase("p")) {
				commandPrint(words);
			}
			else if (word.equalsIgnoreCase("pq")) {	// print queue
				commandPrintQueue();
			}
			else if (word.equalsIgnoreCase("ps")) {	// print start concepts
				commandPrintStartConcepts();
			}
			else if (word.equalsIgnoreCase("list")) {
				commandList(words);
			}
			else if (word.equalsIgnoreCase("lr")) {
				commandListRelations(words);
			}
			else if (word.equalsIgnoreCase("lc")) {
				commandListConcepts();
			}
			else if (word.equalsIgnoreCase("lw")) {
				commandListWorkers();
			}
			else if (word.equalsIgnoreCase("lt")) {
				commandListThreads();
			}
			else if (word.equalsIgnoreCase("new")) {
				commandNew(words);
				return;
			}
			else if (word.equalsIgnoreCase("nc")) {
				commandNewConcept(words);
				return;
			}
			else if (word.equalsIgnoreCase("nr")) {
				commandNewRelation(words);
				return;
			}
			else if (word.equalsIgnoreCase("set")) {
				commandSet(words);
			}
			else if (word.equalsIgnoreCase("clear") || word.equalsIgnoreCase("delete:all")) {
				commandClear();
			}
			else if (word.equalsIgnoreCase("delete") || word.equalsIgnoreCase("del")) {
				commandDelete(words);
			}
			else if (word.equalsIgnoreCase("load")) {
				commandLoad(words);
			}
			else if (word.equalsIgnoreCase("save")) {
				commandSave(words);
			}
			else if (word.equalsIgnoreCase("path")) {
				commandPath(words);
			}
			else if (word.equalsIgnoreCase("workers") || word.equalsIgnoreCase("worker")) {
				commandWorker(words);
			}
			else { 
				println(" -> unknown command");
			}
		}
	}
	
	private void commandRun() {
		running = true;
		println("Matrix.run...");
		println("  stopping workers...");
		commandStop();
		println("  resetting clock...");
		clock.reset();
		println("    used time: " + clock.getMillis() + "ms");
		println("  preparing working queue...");
		prepareWorkingQueue();
		println("  amount starting-concepts: " + startConcepts.size());
		println("  pass run-command to workers...");
		setWorkerOrder("run");
		int countRunningWorkers = 0;
		while (countRunningWorkers < workers.size()) {
			countRunningWorkers = 0;
			for (Worker w: workers) {
				if (w.getStatus().equals("running")) {
					countRunningWorkers++;
				}
			}
		}
		println(" -> all workers are running");
		println(" -> starting clock for measurement");
		clock.start();
	}
	
	private void commandStop() {
		clock.stop();
		println(" -> used time: " + clock.getMillis() + "ms");
		println(" -> stopping workers...");
		setWorkerOrder("stop");
		int countStoppedWorkers = 0;
		while (countStoppedWorkers < workers.size()) {
			countStoppedWorkers = 0;
			for (Worker w: workers) {
				if (w.getStatus().equals("stopped")) {
					countStoppedWorkers++;
				}
			}
		}
		println(" -> all workers stopped");
		running = false;
	}
	
	private void commandProceed() {
		running = true;
		println(" -> starting workers without resetting...");
		setWorkerOrder("run");
		int countWorkers = 0;
		while (countWorkers < workers.size()) {
			countWorkers = 0;
			for (Worker w: workers) {
				if (w.getStatus().equals("running")) {
					countWorkers++;
				}
			}
		}
		println(" -> all workers running");
	}
	
	private void commandReset() {
		reset();
	}
	
	private void commandPrint(LinkedList<String> words) {
		if (words.isEmpty()) {
			println(" -> What shall be printed?");
			return;
		}
		String con = words.poll();
		if (con.equals("queue")) {
			commandPrintQueue();
			return;
		}
		else if (con.equals("start")) {
			commandPrintStartConcepts();
			return;
		}
		else if (con.startsWith("\"")) {
			String text = "";
			if (con.contains("\"")) {
				
			}
			else {
				println(" -> Didn't find closing-\" ... not printing");
				words.addFirst(con);
			}
			return;
		}
		
		else if (con.startsWith("c:")){
			Pair<String, String> p = Scanner.cutFront(con, 2);
			con = p.value2;
			// TODO looking for .
			// possible: print c:one.1 -> relation 1 of concept one shall be printed
			// TODO implement: print c:one.value, c:one.relCount -> change class concept
			return;
		}
		// TODO no valid print-argument -> the polled word has to be put back into word-list 
	}
	
	/**
	 * prints all information/settings about the current relation
	 */
	private void commandPrintRelation(Relation r) {
		if (r != null) {
			println(r.toString());
		}
		else { println(" -> null"); }
	}
	
	/**
	 * prints all information/settings about the current concept
	 */
	private void commandPrintConcept(Concept c) {
		if (c != null) {
			println(c.toString());
		}
		else { println(" -> null"); }
	}
	
	private void commandPrintQueue() {
		String s = " -> ";
		for (Concept c: workingQueue) {
			s += c.getName() + ", ";
		}
		s += "\n";
		s += " -> " + workingQueue.size() + " concept(s) in workingQueue";
		println(s);
	}
	
	private void commandPrintStartConcepts() {
		String s = " -> ";
		for (Concept c: startConcepts) {
			s += c.getName() + ", ";
		}
		s += "\n";
		s += " -> " +startConcepts.size() + "start-concept(s)";
		println(s);
	}
	
	/**
	 * This method looks whether a value or flag shall be set for a relation or a concept.
	 * If there is only the word set, then the software looks for a concept with the name "set".
	 * @param word
	 * @param words
	 */
	private void commandSet(LinkedList<String> words) {
		if (words.isEmpty()) {
			println(" -> The object that shall be changed is missing.");
			return;
		}
		String word = words.poll();
		Concept c = findConcept(word);
		if (c == null) {
			println(" -> The concept \"" + word + "\" was not found.");
			words.addFirst(word);
			return;
		}
		// found the concept
		if (words.isEmpty()) {
			println(" -> Changing-options are missing. What exactly shall be changed?");
			return;
		}
		word = words.peek(); // have a look if the next word is a number then a relation shall be changed
		try {
			@SuppressWarnings("unused")
			int relNumber = Integer.valueOf(word);
			commandSetRelation(c, words);
		}
		catch(Exception e) {
			println("changing concept");
			commandSetConcept(c, words);
		}
	}
			
	private void commandSetConcept(Concept c, LinkedList<String> words) {
		synchronized(this) {
			if (c == null) {
				println(" -> You gave concept = null - it can't be changed.");
				return;
			}
			if (words.isEmpty()) {
				protocoll(" -> no options were given");
				return;
			}
			boolean valueChanged = false;	// for input-concept/ adding to workingQueue
			while (!words.isEmpty()) {
				String word = words.poll();
				if (word.equalsIgnoreCase("start")) { 
					c.switchStartConcept();
					checkStartConceptsList(c);
					protocoll(" -> changed startingConcept of \"" + c.getName() + "\" to " + c.isStartConcept());
				}
				else if (word.equalsIgnoreCase("start:true") || word.equalsIgnoreCase("startt")) { 
					c.isStartConcept(true);
					protocoll(" -> changed startingConcept of \"" + c.getName() + "\" to " + c.isStartConcept());
				}
				else if (word.equalsIgnoreCase("start:false") || word.equalsIgnoreCase("startf")) { 
					c.isStartConcept(false);
					protocoll(" -> changed startingConcept of \"" + c.getName() + "\" to " + c.isStartConcept());
				}
				else if (word.equalsIgnoreCase("stop")) { 
					c.switchStopConcept();
					protocoll(" -> changed stoppingConcept of \"" + c.getName() + "\" to " + c.isStopConcept());
				}
				else if (word.equalsIgnoreCase("stop:true") || word.equalsIgnoreCase("stopt")) { 
					c.isStopConcept(true);
					protocoll(" -> changed stoppingConcept of \"" + c.getName() + "\" to " + c.isStopConcept());
				}
				else if (word.equalsIgnoreCase("stop:false") || word.equalsIgnoreCase("stopf")) { 
					c.isStopConcept(false);
					protocoll(" -> changed stoppingConcept of \"" + c.getName() + "\" to " + c.isStopConcept());
				}
				else if (word.equalsIgnoreCase("repetitive") || word.equalsIgnoreCase("rep")) { 
					c.switchRepetitive();
					protocoll(" -> changed repetitive of \"" + c.getName() + "\" to " + c.isRepetitive());
				}
				else if (word.equalsIgnoreCase("repetitive:true") || word.equalsIgnoreCase("rep:true")) { 
					c.isRepetitive(true);
					protocoll(" -> changed repetitive of \"" + c.getName() + "\" to " + c.isRepetitive());
				}
				else if (word.equalsIgnoreCase("repetitive:false") || word.equalsIgnoreCase("rep:false")) { 
					c.isRepetitive(false);
					protocoll(" -> changed repetitive of \"" + c.getName() + "\" to " + c.isRepetitive());
				}
				else if (word.startsWith("threshold:") || word.startsWith("thr:")) {
					word = word.replaceFirst("threshold:", "");
					word = word.replaceFirst("thr:", "");
					if (commandSetThreshold(c, word)) {
						if (c.hasDynamicThreshold()) {
							println(" -> WARNING: the threshold of the concept " + c.getName() + " is set dynamic.");
							println(" ->          Setting the threshold manually may have no effect.");
						}
						else {
							protocoll(" -> the threshold of the concept " + c.getName() + " has been set to " + c.getThresholdValue());
						}
					}
				}
				else if (word.equalsIgnoreCase("relationCount") || word.equalsIgnoreCase("relCount") || word.equalsIgnoreCase("rc")) {
					c.switchRelationCount();
					protocoll(" -> the property relationCount of the concept " + c.getName() + " has been set to " + c.useRelationCount());
				}
				else if (word.equalsIgnoreCase("relationCount:true") || word.equalsIgnoreCase("relCount:true") || word.equalsIgnoreCase("rct")) {
					c.useRelationCount(true);
					protocoll(" -> the property relationCount of the concept " + c.getName() + " has been set to " + c.useRelationCount());
				}
				else if (word.equalsIgnoreCase("relationCount:false") || word.equalsIgnoreCase("relCount:false") || word.equalsIgnoreCase("rcf")) {
					c.useRelationCount(false);
					protocoll(" -> the property relationCount of the concept " + c.getName() + " has been set to " + c.useRelationCount());
				}
				else if (word.equalsIgnoreCase("positiveThreshold") || word.equalsIgnoreCase("posThr") || word.equalsIgnoreCase("pt")) {
					c.switchPositiveThreshold();
					protocoll(" -> positiveThreshold of the concept " + c.getName() + " has been set to " + c.usePositiveThreshold());
				}
				else if (word.equalsIgnoreCase("positiveThreshold:true") || word.equalsIgnoreCase("posThr:true") || word.equalsIgnoreCase("ptt")) {
					c.usePositiveThreshold(true);
					protocoll(" -> positiveThreshold of the concept " + c.getName() + " has been set to " + c.usePositiveThreshold());
				}
				else if (word.equalsIgnoreCase("positiveThreshold:false") || word.equalsIgnoreCase("posThr:false") || word.equalsIgnoreCase("ptf")) {
					c.usePositiveThreshold(false);
					protocoll(" -> positiveThreshold of the concept " + c.getName() + " has been set to " + c.usePositiveThreshold());
				}
				else if (word.equalsIgnoreCase("negativeThreshold") || word.equalsIgnoreCase("negThr") || word.equalsIgnoreCase("nt")) {
					c.switchNegativeThreshold();
					protocoll(" -> negativeThreshold of the concept " + c.getName() + " has been set to " + c.useNegativeThreshold());
				}
				else if (word.equalsIgnoreCase("negativeThreshold:true") || word.equalsIgnoreCase("negThr:true") || word.equalsIgnoreCase("ntt")) {
					c.useNegativeThreshold(true);
					protocoll(" -> negativeThreshold of the concept " + c.getName() + " has been set to " + c.useNegativeThreshold());
				}
				else if (word.equalsIgnoreCase("negativeThreshold:false") || word.equalsIgnoreCase("negThr:false") || word.equalsIgnoreCase("ntf")) {
					c.useNegativeThreshold(false);
					protocoll(" -> negativeThreshold of the concept " + c.getName() + " has been set to " + c.useNegativeThreshold());
				}
				else if (word.equalsIgnoreCase("valueThreshold") || word.equalsIgnoreCase("valThr") || word.equalsIgnoreCase("vt")) {
					c.switchValueThreshold();
					protocoll(" -> valueThreshold of the concept " + c.getName() + " has been set to " + c.useValueThreshold());
				}
				else if (word.equalsIgnoreCase("valueThreshold:true") || word.equalsIgnoreCase("valThr:true") || word.equalsIgnoreCase("vtt")) {
					c.useValueThreshold(true);
					protocoll(" -> valueThreshold of the concept " + c.getName() + " has been set to " + c.useValueThreshold());
				}
				else if (word.equalsIgnoreCase("valueThreshold:false") || word.equalsIgnoreCase("valThr:false") || word.equalsIgnoreCase("vtf")) {
					c.useValueThreshold(false);
					protocoll(" -> valueThreshold of the concept " + c.getName() + " has been set to " + c.useValueThreshold());
				}
				else if (word.startsWith("dynamicThresholdSource:") || word.startsWith("dynThrSou:") || word.equalsIgnoreCase("dts:")) {
					commandSetDynamicThresholdSource(c, word);
					protocoll(" -> the dynamic-value-source of the Relation" + c.getName() + " is now " + c.getDynamicThresholdSource().getName());
				}
				else if (word.equalsIgnoreCase("activeCondition:<") || word.equalsIgnoreCase("actCond:<") || word.equalsIgnoreCase("ac<")) {
					c.setActiveCondition("<");
					protocoll(" -> property active of the concept " + c.getName() + " has been set to " + c.getActiveCondition());
				}
				else if (word.equalsIgnoreCase("activeCondition:<=") || word.equalsIgnoreCase("actCond:<=") || word.equalsIgnoreCase("ac<=")) {
					c.setActiveCondition("<=");
					protocoll(" -> property active of the concept " + c.getName() + " has been set to " + c.getActiveCondition());
				}
				else if (word.equalsIgnoreCase("activeCondition:=") || word.equalsIgnoreCase("actCond:=") || word.equalsIgnoreCase("ac=")) {
					c.setActiveCondition("=");
					protocoll(" -> property active of the concept " + c.getName() + " has been set to " + c.getActiveCondition());
				}
				else if (word.equalsIgnoreCase("activeCondition:>=") || word.equalsIgnoreCase("actCond:>=") || word.equalsIgnoreCase("ac>=")) {
					c.setActiveCondition(">=");
					protocoll(" -> property active of the concept " + c.getName() + " has been set to " + c.getActiveCondition());
				}
				else if (word.equalsIgnoreCase("activeCondition:>") || word.equalsIgnoreCase("actCond:>") || word.equalsIgnoreCase("ac>")) {
					c.setActiveCondition(">");
					protocoll(" -> property active of the concept " + c.getName() + " has been set to " + c.getActiveCondition());
				}
				else if (word.equalsIgnoreCase("thresholdCounters") || word.equalsIgnoreCase("thrCount") || word.equalsIgnoreCase("tc")) {
					c.switchThresholdAlwaysNull();
					protocoll(" -> changed thresholdAlwaysNull of \"" + c.getName() + "\" to " + c.isThresholdAlwaysNull());
				}
				else if (word.equalsIgnoreCase("thresholdCounters:true") || word.equalsIgnoreCase("thrCount:true") || word.equalsIgnoreCase("tct")) {
					c.isThresholdAlwaysNull(true);
					protocoll(" -> changed thresholdAlwaysNull of \"" + c.getName() + "\" to " + c.isThresholdAlwaysNull());
				}
				else if (word.equalsIgnoreCase("thresholdCounters:false") || word.equalsIgnoreCase("thrCount:false") || word.equalsIgnoreCase("tcf")) {
					c.isThresholdAlwaysNull(false);
					protocoll(" -> changed thresholdAlwaysNull of \"" + c.getName() + "\" to " + c.isThresholdAlwaysNull());
				}
				else if (word.equalsIgnoreCase("resetOne")) { 
					c.switchResetOne();
					protocoll(" -> changed thresholdResetAfterOneRelation of \"" + c.getName() + "\" to " + c.isResetOne());
				}
				else if (word.equalsIgnoreCase("resetOne:true")) { 
					c.isResetOne(true);
					protocoll(" -> changed thresholdResetAfterOneRelation of \"" + c.getName() + "\" to " + c.isResetOne());
				}
				else if (word.equalsIgnoreCase("resetOne:false")) { 
					c.isResetOne(false);
					protocoll(" -> changed thresholdResetAfterOneRelation of \"" + c.getName() + "\" to " + c.isResetOne());
				}
				else if (word.equalsIgnoreCase("priority") || word.equalsIgnoreCase("pri")) {
					c.switchWorkingQueuePriority();
					protocoll(" -> the workingQueuePriority of \"" + c.getName() + "\" has been set to " + c.getWorkingQueuePriority());
				}
				else if (word.equalsIgnoreCase("priority:high") || word.equalsIgnoreCase("pri:high") || word.equalsIgnoreCase("ph")) {
					c.setWorkingQueuePriority("high");
					protocoll(" -> the workingQueuePriority of \"" + c.getName() + "\" has been set to " + c.getWorkingQueuePriority());
				}
				else if (word.equalsIgnoreCase("priority:low") || word.equalsIgnoreCase("pri:low") || word.equalsIgnoreCase("pl")) {
					c.setWorkingQueuePriority("low");
					protocoll(" -> the workingQueuePriority of \"" + c.getName() + "\" has been set to " + c.getWorkingQueuePriority());
				}
				else if (word.equalsIgnoreCase("zero") || word.equalsIgnoreCase("zer") || word.equalsIgnoreCase("z")) {
					c.switchComesIntoQueueWhenZero();
					protocoll(" -> the queueWhenZero-property of \"" + c.getName() + "\" has been set to " + c.comesIntoQueueWhenZero());
				}
				else if (word.equalsIgnoreCase("zero:true") || word.equalsIgnoreCase("zer:true") || word.equalsIgnoreCase("zt")) {
					c.comesIntoQueueWhenZero(true);
					protocoll(" -> \"" + c.getName() + "\" now comes into workingQueue even then when change was 0");
				}
				else if (word.equalsIgnoreCase("zero:false") || word.equalsIgnoreCase("zer:false") || word.equalsIgnoreCase("zf")) {
					c.comesIntoQueueWhenZero(false);
					protocoll(" -> \"" + c.getName() + "\" no longer comes into workingQueue when change was 0");
				}
				else if (word.equalsIgnoreCase("input") || word.equalsIgnoreCase("inp")) {
					c.switchInput();
					protocoll(" -> the input-property of \"" + c.getName() + "\" has been set to " + c.isInput());
				}
				else if (word.equalsIgnoreCase("input:true") || word.equalsIgnoreCase("inp:true") || word.equalsIgnoreCase("it")) {
					c.isInput(true);
					protocoll(" -> \"" + c.getName() + "\" is now an input-concept");
				}
				else if (word.equalsIgnoreCase("input:false") || word.equalsIgnoreCase("inp:false") || word.equalsIgnoreCase("if")) {
					c.isInput(false);
					protocoll(" -> \"" + c.getName() + "\" is no longer an input-concept");
				}
				else if (word.startsWith("add:") || word.startsWith("a:")) {
					word = word.replaceFirst("add:", "");
					word = word.replaceFirst("a:", "");
					double v;
					try {
						v = Double.valueOf(word);
						c.setValue(c.getValue() + v);
						protocoll(" -> the value of " + c.getName() + " has been set to " + c.getValue());
						valueChanged = true; // if this is an input-concept, true causes an insertion of the input-concept into workingQueue
					}
					catch (Exception e) {
						println(" -> \"" + word + "\" is not a valid double-value");
					}
				}
				else if (word.startsWith("value:") || word.startsWith("val:") || word.startsWith("v:")) {
					word = word.replaceFirst("value:", "");
					word = word.replaceFirst("val:", "");
					word = word.replaceFirst("v:", "");
					double v;
					try {
						v = Double.valueOf(word);
						c.setValue(v);
						protocoll(" -> the value of " + c.getName() + " has been set to " + c.getValue());
						valueChanged = true; // if this is an input-concept, true causes an insertion of the input-concept into workingQueue
					}
					catch (Exception e) {
						println(" -> \"" + word + "\" is not a valid double-value");
					}
				}
				else if (word.startsWith("startValue:") || word.startsWith("startVal:") || word.startsWith("sv:")) {
					word = word.replaceFirst("startValue:", "");
					word = word.replaceFirst("startVal:", "");
					word = word.replaceFirst("sv:", "");
					double v;
					try {
						v = Double.valueOf(word);
						c.setStartValue(v);
						protocoll(" -> the startValue of " + c.getName() + " has been set to " + c.getStartValue());
					}
					catch (Exception e) {
						println(" -> \"" + word + "\" is not a valid double-value");
						return;
					}
				}
				else if (word.startsWith("command:") || word.startsWith("com:") || word.startsWith("c:")) {
					word = word.replaceFirst("command:", "");
					word = word.replaceFirst("com:", "");
					word = word.replaceFirst("c:", "");
					//TODO
				}
				else {
					println(" -> \"" + word + "\" is an invalid option for concepts.");
				}
			}
			if (valueChanged && c.isInput()) {
				putIntoWorkingQueue(c);
			}
		}
	}
	
	private void commandSetRelation(Concept c, LinkedList<String> words) {
		if (c == null) {
			println(" -> You gave a concept = null -> no relations, no change.");
			return;
		}
		if (words.isEmpty()) {
			println(" -> Relation-index is missing.");
			return;
		}
		String word = words.poll();
		int relid;
		try {
			relid = Integer.valueOf(word);
		}
		catch (Exception e) { // converting into int didnt work
			println(" -> \"" + word + "\" is an invalid relation-index.");
			return;
		}
		// try to find the relation
		Relation r = findRelation(c, relid);
		if (r == null) {
			println(" -> Didn't find a relation with the given id \"" + relid + "\".");
			return;
		}
		while (!words.isEmpty()) {
			word = words.poll();
			// TODO change source
			// TODO change target
			// TODO change indexed value
			// TODO change sourceIndex
			// TODO change targetIndex
			if (word.startsWith("value:") || word.startsWith("val:") || word.startsWith("v:")) {
				word = word.replaceFirst("value:", "");
				word = word.replaceFirst("val:", "");
				word = word.replaceFirst("v:", "");
				double v;
				try {
					v = Double.valueOf(word);
				}
				catch (Exception e) {
					println(" -> \"" + word + "\" is not a valid double-value.");
					return;
				}
				r.setValue(v);
				protocoll(" -> the value of relation " + relid + " of " + c.getName() + " has been set to " + v);
			}
			else if (word.startsWith("calcSource:") || word.startsWith("cs:")) {
				word = word.replaceFirst("calcSource:", "");
				word = word.replaceFirst("cs:", "");
				Concept con = findConcept(word); 
				if (con == null) {
					println(" -> a concept \"" + word + "\" wasn't found.");
				}
				else {	// concept was found
					r.setCalculationSource(con);
					protocoll(" -> calculation-source of a relation of the concept \"" + r.getSource().getName() + "\" has been set to \"" + word + "\"");
				}
			}
			else if (word.startsWith("dynamicValueSource:") || word.startsWith("dynValSou:") || word.startsWith("dvs:")) {
				commandSetDynamicValueSource(r, word);
				protocoll(" -> the dynamic-value-source of the Relation" + relid + " of " + c.getName() + " is now " + r.getDynamicValueSource().getName());
			}
			else if (word.equalsIgnoreCase("move")) {
				r.switchMove();
				protocoll(" -> the property move of relation " + relid + " of " + c.getName() + " has been set to " + r.shallMove());
			}
			else if (word.equalsIgnoreCase("move:true") || word.equalsIgnoreCase("mt")) {
				r.shallMove(true);
				protocoll(" -> the property move of relation " + relid + " of " + c.getName() + " has been set to " + r.shallMove());
			}
			else if (word.equalsIgnoreCase("move:false") || word.equalsIgnoreCase("mf")) {
				r.shallMove(false);
				protocoll(" -> the property move of relation " + relid + " of " + c.getName() + " has been set to " + r.shallMove());
			}
			else if (word.startsWith("threshold:") || word.startsWith("thr:")) {
				word = word.replaceFirst("threshold:", "");
				word = word.replaceFirst("thr:", "");
				if (commandSetThreshold(r, word)) {
					if (r.hasDynamicThreshold()) {
						println(" -> WARNING: the threshold of the relation " + r.getId() + " of " + r.getSource().getName() + " is set dynamic.");
						println(" ->          Setting the threshold manually may have no effect.");
					}
					else {
						protocoll(" -> the threshold of the relation " + r.getId() + " of " + r.getSource().getName() + " has been set to " + r.getThresholdValue());
					}
				}
			}
			else if (word.equalsIgnoreCase("relationCount") || word.equalsIgnoreCase("relCount") || word.equalsIgnoreCase("rc")) {
				r.switchRelationCount();
				protocoll(" -> the property relationCount of the relation " + relid + " of " + c.getName() + " has been set to " + r.useRelationCount());
			}
			else if (word.equalsIgnoreCase("relationCount:true") || word.equalsIgnoreCase("relCount:true") || word.equalsIgnoreCase("rct")) {
				r.useRelationCount(true);
				protocoll(" -> the property relationCount of the relation " + relid + " of " + c.getName() + " has been set to " + r.useRelationCount());
			}
			else if (word.equalsIgnoreCase("relationCount:false") || word.equalsIgnoreCase("relCount:false") || word.equalsIgnoreCase("rcf")) {
				r.useRelationCount(false);
				protocoll(" -> the property relationCount of the relation " + relid + " of " + c.getName() + " has been set to " + r.useRelationCount());
			}
			else if (word.equalsIgnoreCase("positiveThreshold") || word.equalsIgnoreCase("posThr") || word.equalsIgnoreCase("pt")) {
				r.switchPositiveThreshold();
				protocoll(" -> positiveThreshold of the relation " + relid + " of " + c.getName() + " has been set to " + r.usePositiveThreshold());
			}
			else if (word.equalsIgnoreCase("positiveThreshold:true") || word.equalsIgnoreCase("posThr:true") || word.equalsIgnoreCase("ptt")) {
				r.usePositiveThreshold(true);
				protocoll(" -> positiveThreshold of the relation " + relid + " of " + c.getName() + " has been set to " + r.usePositiveThreshold());
			}
			else if (word.equalsIgnoreCase("positiveThreshold:false") || word.equalsIgnoreCase("posThr:false") || word.equalsIgnoreCase("ptf")) {
				r.usePositiveThreshold(false);
				protocoll(" -> positiveThreshold of the relation " + relid + " of " + c.getName() + " has been set to " + r.usePositiveThreshold());
			}
			else if (word.equalsIgnoreCase("negativeThreshold") || word.equalsIgnoreCase("negThr") || word.equalsIgnoreCase("nt")) {
				r.switchNegativeThreshold();
				protocoll(" -> negativeThreshold of the relation " + relid + " of " + c.getName() + " has been set to " + r.useNegativeThreshold());
			}
			else if (word.equalsIgnoreCase("negativeThreshold:true") || word.equalsIgnoreCase("negThr:true") || word.equalsIgnoreCase("ntt")) {
				r.useNegativeThreshold(true);
				protocoll(" -> negativeThreshold of the relation " + relid + " of " + c.getName() + " has been set to " + r.useNegativeThreshold());
			}
			else if (word.equalsIgnoreCase("negativeThreshold:false") || word.equalsIgnoreCase("negThr:false") || word.equalsIgnoreCase("ntf")) {
				r.useNegativeThreshold(false);
				protocoll(" -> negativeThreshold of the relation " + relid + " of " + c.getName() + " has been set to " + r.useNegativeThreshold());
			}
			else if (word.equalsIgnoreCase("valueThreshold") || word.equalsIgnoreCase("valThr") || word.equalsIgnoreCase("vt")) {
				r.switchValueThreshold();
				protocoll(" -> valueThreshold of the relation " + relid + " of " + c.getName() + " has been set to " + r.useValueThreshold());
			}
			else if (word.equalsIgnoreCase("valueThreshold:true") || word.equalsIgnoreCase("valThr:true") || word.equalsIgnoreCase("vtt")) {
				r.useValueThreshold(true);
				protocoll(" -> valueThreshold of the relation " + relid + " of " + c.getName() + " has been set to " + r.useValueThreshold());
			}
			else if (word.equalsIgnoreCase("valueThreshold:false") || word.equalsIgnoreCase("valThr:false") || word.equalsIgnoreCase("vtf")) {
				r.useValueThreshold(false);
				protocoll(" -> valueThreshold of the relation " + relid + " of " + c.getName() + " has been set to " + r.useValueThreshold());
			}
			else if (word.startsWith("dynamicThresholdSource:") || word.startsWith("dynThrSou:") || word.equalsIgnoreCase("dts:")) {
				commandSetDynamicThresholdSource(r, word);
				protocoll(" -> the dynamic-value-source of the Relation" + relid + " of " + c.getName() + " is now " + r.getDynamicThresholdSource().getName());
			}
			else if (word.equalsIgnoreCase("activeCondition:<") || word.equalsIgnoreCase("actCond:<") || word.equalsIgnoreCase("ac<")) {
				r.setActiveCondition("<");
				protocoll(" -> property activeCondition of the relation " + relid + " of " + c.getName() + " has been set to " + r.getActiveCondition());
			}
			else if (word.equalsIgnoreCase("activeCondition:<=") || word.equalsIgnoreCase("actCond:<=") || word.equalsIgnoreCase("ac<=")) {
				r.setActiveCondition("<=");
				protocoll(" -> property activeCondition of the relation " + relid + " of " + c.getName() + " has been set to " + r.getActiveCondition());
			}
			else if (word.equalsIgnoreCase("activeCondition:=<") || word.equalsIgnoreCase("actCond:=<") || word.equalsIgnoreCase("ac=<")) {
				r.setActiveCondition("<=");
				protocoll(" -> property activeCondition of the relation " + relid + " of " + c.getName() + " has been set to " + r.getActiveCondition());
			}
			else if (word.equalsIgnoreCase("activeCondition:=") || word.equalsIgnoreCase("actCond:=") || word.equalsIgnoreCase("ac=")) {
				r.setActiveCondition("=");
				protocoll(" -> property activeCondition of the relation " + relid + " of " + c.getName() + " has been set to " + r.getActiveCondition());
			}
			else if (word.equalsIgnoreCase("activeCondition:>=") || word.equalsIgnoreCase("actCond:>=") || word.equalsIgnoreCase("ac>=")) {
				r.setActiveCondition(">=");
				protocoll(" -> property activeCondition of the relation " + relid + " of " + c.getName() + " has been set to " + r.getActiveCondition());
			}
			else if (word.equalsIgnoreCase("activeCondition:=>") || word.equalsIgnoreCase("actCond:=>") || word.equalsIgnoreCase("ac=>")) {
				r.setActiveCondition(">=");
				protocoll(" -> property activeCondition of the relation " + relid + " of " + c.getName() + " has been set to " + r.getActiveCondition());
			}
			else if (word.equalsIgnoreCase("activeCondition:>") || word.equalsIgnoreCase("actCond:>") || word.equalsIgnoreCase("ac>")) {
				r.setActiveCondition(">");
				protocoll(" -> property activeCondition of the relation " + relid + " of " + c.getName() + " has been set to " + r.getActiveCondition());
			}
			else if (word.equalsIgnoreCase("relationCountReset") || word.equalsIgnoreCase("relCountRes") || word.equalsIgnoreCase("rcr")) {
				r.switchRelCountReset();
				protocoll(" -> property relCountReset of the relation " + relid + " of " + c.getName() + " has been set to " + r.useRelCountReset());
			}
			else if (word.equalsIgnoreCase("relationCountReset:true") || word.equalsIgnoreCase("relCountRes:true") || word.equalsIgnoreCase("rcrt")) {
				r.useRelCountReset(true);
				protocoll(" -> property relCountReset of the relation " + relid + " of " + c.getName() + " has been set to " + r.useRelCountReset());
			}
			else if (word.equalsIgnoreCase("relationCountReset:false") || word.equalsIgnoreCase("relCountRes:false") || word.equalsIgnoreCase("rcrf")) {
				r.useRelCountReset(false);
				protocoll(" -> property relCountReset of the relation " + relid + " of " + c.getName() + " has been set to " + r.useRelCountReset());
			}
			else if (word.equalsIgnoreCase("negativeThresholdReset") || word.equalsIgnoreCase("negThrRes") || word.equalsIgnoreCase("ntr")) {
				r.switchNegThresholdReset();
				protocoll(" -> property negThresholdReset of the relation " + relid + " of " + c.getName() + " has been set to " + r.useNegThresholdReset());
			}
			else if (word.equalsIgnoreCase("negativeThresholdReset:true") || word.equalsIgnoreCase("negThrRes:true") || word.equalsIgnoreCase("ntrt")) {
				r.useNegThresholdReset(true);
				protocoll(" -> property negThresholdReset of the relation " + relid + " of " + c.getName() + " has been set to " + r.useNegThresholdReset());
			}
			else if (word.equalsIgnoreCase("negativeThresholdReset:false") || word.equalsIgnoreCase("negThrRes:false") || word.equalsIgnoreCase("ntrf")) {
				r.useNegThresholdReset(false);
				protocoll(" -> property negThresholdReset of the relation " + relid + " of " + c.getName() + " has been set to " + r.useNegThresholdReset());
			}
			else if (word.equalsIgnoreCase("positiveThresholdReset") || word.equalsIgnoreCase("posThrRes") || word.equalsIgnoreCase("ptr")) {
				r.switchPosThresholdReset();
				protocoll(" -> property posThresholdReset of the relation " + relid + " of " + c.getName() + " has been set to " + r.usePosThresholdReset());
			}
			else if (word.equalsIgnoreCase("positiveThresholdReset:true") || word.equalsIgnoreCase("posThrRes:true") || word.equalsIgnoreCase("ptrt")) {
				r.usePosThresholdReset(true);
				protocoll(" -> property posThresholdReset of the relation " + relid + " of " + c.getName() + " has been set to " + r.usePosThresholdReset());
			}
			else if (word.equalsIgnoreCase("positiveThresholdReset:false") || word.equalsIgnoreCase("posThrRes:false") || word.equalsIgnoreCase("ptrf")) {
				r.usePosThresholdReset(false);
				protocoll(" -> property posThresholdReset of the relation " + relid + " of " + c.getName() + " has been set to " + r.usePosThresholdReset());
			}
			else {
				println(" -> \"" + word + "\" is an invalid changing-option for relations.");
			}
		}
	}
	
	private void commandSetDynamicValueSource(Relation r, String word) {
		word = word.replaceFirst("dynamicValueSource:", "");
		word = word.replaceFirst("dynValSou:", "");
		word = word.replaceFirst("dvs:", "");
		Concept c = findConcept(word);
		if (c == null) c = addConcept(word);
		r.setDynamicValueSource(c);
	}
	
	private void commandSetDynamicThresholdSource(UniSimObject obj, String word) {
		word = word.replaceFirst("dynamicThresholdSource:", "");
		word = word.replaceFirst("dynThrSou:", "");
		word = word.replaceFirst("dts:", "");
		Concept c = addConcept(word);
		obj.setDynamicThresholdSource(c);
	}
	
	/**
	 * obj can be a relation or a concept. word is a string that should stand for the new
	 * value that is stored in the threshold-property of the unisim-object.
	 * This method is only called by commandSetConcept and commandSetRelation
	 * @param obj
	 * @param word
	 */
	private boolean commandSetThreshold(UniSimObject obj, String word) {
		double v;
		try {
			v = Double.valueOf(word);
		}
		catch (Exception e) {
			println(" -> \"" + word + "\" is not valid double-value.");
			return false;
		}
		obj.setThresholdValue(v);
		return true;
	}
	
	private void commandList(LinkedList<String> words) {
		if (words.isEmpty()) {
			println(" -> the object that shall be listed is missing");
			return;
		}
		String word = words.poll();
		if (word.equalsIgnoreCase("relations") || word.equalsIgnoreCase("rel") || word.equalsIgnoreCase("r")) { commandListRelations(words); }
		else if (word.equalsIgnoreCase("concepts") || word.equalsIgnoreCase("con") || word.equalsIgnoreCase("c")) { commandListConcepts(); }
		else if (word.equalsIgnoreCase("workers")) { commandListWorkers(); }
		else if (word.equalsIgnoreCase("threads")) { commandListThreads(); }
		else {
			println(" -> unknown object for command list");
		}
	}
	
	/**
	 * Shows an alphabetical list of all concept-names.
	 */
	private void commandListConcepts() {
		ArrayList<Concept> list = concepts.traverseInOrder();
		if (list == null) {
			println(" -> there are no concepts");
			return;
		}
		for (Concept c: list) {
			println("  " + c.toShortString());
		}
		println("  total amount of Concepts: " + list.size());
	}
	
	/**
	 * Shows all relations of the current-concept in a short version.
	 */
	private void commandListRelations(LinkedList<String> words) {
		if (words.isEmpty()) {
			println(" -> The concept-name from which I shall list the relations is missing.");
			return;
		}
		String word = words.poll();
		Concept c = findConcept(word);
		if (c == null) {
			println(" -> Didn't find a concept with the name " + word);
			return;
		}
		boolean shortVersion = true;
		if (!words.isEmpty()) {
			word = words.poll();
			if (word.equals("-long")) { shortVersion = false; }
		}
		if (c.getRelations().getSize() == 0) {
			println(" -> Concept \"" + word + "\" has no relations.");
			return;
		}
		LinkedBlockingQueue<Relation> list = c.getRelationsLBQ();
		for (Relation r: list) {
			if (shortVersion) { println(r.toShortString()); }
			else { println(r.toString()); }
		}
	}
			
	/**
	 * printlns all threads and whether they are alive or not.
	 */
	private void commandListThreads() {
		for (Thread t: threads) {
			if (t.isAlive()) { println(" -> thread " + t.getName() + " is alive"); }
			else { println(" -> thread " + t.getName() + " is dead"); }					
		}
	}
	
	/**
	 * printlns information about working-status of workers
	 */
	private void commandListWorkers() {
		for (Worker w: workers) {
			println(" -> " + w.getInfo());				
		}
	}
		
	/**
	 * Try to finds a concept with the given name and returns it if found.
	 */
	private Concept findConcept(String name) {
		Concept c = new Concept(name);
		return findConcept(c);
	}
	
	/**
	 * Tries to find a given concept regarding name and returns if found.
	 */
	private Concept findConcept(Concept c) {
		return concepts.find(c);
	}
	
	/**
	 * Tries to find a Relation of the Concept c that has the given id.
	 * @param id
	 * @return
	 */
	private Relation findRelation(Concept c, int id) {
		if (c == null) { return null; }
		Relation r = new RelationAdd(c, c);
		r.setId(id);
		return c.getRelations().find(r);
	}
	
	/**
	 * The command new can create a concept or a relation.
	 * If only the word "new" is given, then the software checks, if "new" is the name of a concept and in case, set it as current concept.
	 * @param w
	 * @param words
	 */
	private void commandNew(LinkedList<String> words) {
		if (words.isEmpty()) {
			println(" -> You forgot to give an object to create!");
			return;
		}
		String word = words.poll();
		if (word.equalsIgnoreCase("concept") || word.equalsIgnoreCase("c")) {
			Concept c = commandNewConcept(words);
			if (c != null) { // concept created successfully
				checkStartConceptsList(c);
			}
			return;
		}
		else if (word.equalsIgnoreCase("relation") || word.equalsIgnoreCase("r")) {
			Relation r = commandNewRelation(words);
			if (r != null) { // successful created relation
				// possibility to react
			}
			return;
		}
		else {
			println("  Command new: object \"" + word + "\" is not a valid object to create.");
		}
	}
	
	private Concept commandNewConcept(LinkedList<String> words) {
		if (words.isEmpty()) {
			println(" -> Concept name is missing.");
			return null;
		}
		else { // there are still words
			String word = words.poll();
			/*if (word.startsWith("-")) {
				println(" -> concept name must not begin with \"-\".");
				return null;
			}*/
			if (word.contains(".")) {
				println(" -> concept name must not contain \".\"");
				words.addFirst(word);
				return null;
			}
			// there is a valid concept-name
			if (findConcept(word) != null) {
				println(" -> A concept with the name \"" + word + "\" already exists.");
				return null;
			}
			Concept c = new Concept(word);
			this.addConcept(c);
			commandSetConcept(c, words);
			protocoll(" -> Concept \"" + word + "\" has been added.");
			return c;
		}
	}
		
	private Relation commandNewRelation(LinkedList<String> words) {
		synchronized(this) {
			if (words.isEmpty()) {
				println(" -> You forgot to give a source-concept.");
				return null;
			}
			else {
				String source = words.poll();
				if (!isValidConceptName(source)) {
					println(" -> " + source + " is an invalid name for source-concept");
					return null;
				}
				if (words.isEmpty()) {
					println(" -> You forgot to give a target-concept.");
					return null;
				}
				String target = words.poll();
				if (!isValidConceptName(target)) {
					println(" -> " + target + " is an invalid name for target-concept");
					return null;
				}
				// look for source concept
				Concept s = findConcept(source);
				if (s == null) {					// if source-concept doesnt exist
					s = addConcept(source);			//   then add that concept to the tree
					protocoll(" -> Source-concept \"" + source + "\" didn't exist and has been created.");
				}
				// look for target concept
				Concept t = findConcept(target);
				if (t == null) {					// if target-concept doesnt exist
					t = addConcept(target);
					protocoll(" -> Target-concept \"" + target + "\" didn't exist and has been created.");
				}
									
				return commandNewRelationCreate(s, t, words);
			}
		}
	}
	
	private Relation commandNewRelationCreate(Concept source, Concept target, LinkedList<String> words) {
		synchronized(this) {
			LinkedList<String> optionList = new LinkedList<String>();
			String type = "%"; // default
			while (!words.isEmpty()) { // looking for the type-options
				String word = words.poll();
				if (word.equalsIgnoreCase("+")) { type = "+"; }
				else if (word.equalsIgnoreCase("%")) { type ="%"; }
				else if (word.equalsIgnoreCase("/")) { type ="/"; }
				else if (word.equalsIgnoreCase("-")) { type ="-"; }
				else if (word.equalsIgnoreCase("*")) { type ="*"; }
				else {
					optionList.add(word); // if word is not a relation-type then add it to the optionList
				}
				
			}
			// create the relation
			Relation r;
			if (type == "+") { r = new RelationAdd(source, target); }
			else if (type == "-") { r = new RelationMinus(source, target); }
			else if (type == "/") { r = new RelationDivide(source, target); }
			else if (type == "*") { r = new RelationMultiply(source, target); }
			else { r = new RelationPercent(source, target); }
			Integer relid = addRelation(r);
			optionList.addFirst(relid.toString());
			commandSetRelation(source, optionList);
			return r;
		}
	}
	
	private void commandDelete(LinkedList<String> words) {
		if (words.isEmpty()) {
			println(" -> What shall be deleted?");
			return;
		}
		String word = words.poll();
		Concept c = findConcept(word);
		if (c == null) {
			protocoll(" -> The concept \"" + word + "\" doesn't exist.");
			return;
		}
		if (words.isEmpty()) {
			concepts.delete(c);
			workingQueue.remove(c);
			if (c.isStartConcept())	{ startConcepts.remove(c); }
			protocoll(" -> The concept \"" + word + "\" has been removed from matrix.");
		}
		else { // there seems to be an index to specify and delete a relation
			word = words.poll();
			Integer id = null;
			try {
				id = Integer.valueOf(word);
			}
			catch (Exception e) { }
			if (id == null) {
				println(" -> \"" + word + "\" is not a valid integer-value (relation id)");
				return;
			}
			if (c.removeRelation(id)) {
				protocoll(" -> Relation " + id + " of " + c.getName() + " has been removed.");
			}
			else {
				protocoll(" -> Relation " + id + " of " + c.getName() + " not found.");
			}
		}
	}
	
	/**
	 * All Concepts and Relations will be deleted.
	 */
	public void commandClear() {
		setWorkerOrder("stop");
		concepts = new AvlTree<Concept>();
		startConcepts.clear();
		workingQueue.clear();
		commands.clear();
		protocoll(" -> The Simulation-Matrix has been cleared completely.");
	}
	
	/**
	 * Loads a simulation-file and execute it's commands.
	 * All settings will be cleared before executing the commands.
	 * @param filename
	 * @throws IOException 
	 */
	public void commandLoad(LinkedList<String> words) throws IOException{
		if (words.isEmpty()) {
			println(" -> filename is missing.");
		}
		String filename = workingPath + words.poll();
		LinkedList<String> lines = getFile(filename);
		if (lines.size() == 0) {
			println(" -> couldn't read any lines from file.");
			return;
		}
		boolean add = false;
		String word = "";
		if (!words.isEmpty()) {
			word = words.poll();
			if (word.equalsIgnoreCase("add")) {
				add = true;
			}
			else {
				println(" -> invalid option " + word);
				return;
			}
		}
		if (add) {
			setWorkerOrder("stop");
		}
		else {
			commandClear();
		}
		println(" -> processing lines...");
		while (!lines.isEmpty()) {
			String com = lines.poll();
			command(com);
		}
		println(" -> processing file finished.");
	}
	
	public void commandSave(LinkedList<String> words) throws IOException {
		if (words.isEmpty()) {
			if (workingPath.isEmpty()) {
				println(" -> path and filename is missing.");
			}
			else {
				println(" -> filename is missing");
			}
			return;
		}
		String word = words.poll();
		word = word.trim();
		if (word.isEmpty()) {
			println(" -> filename is missing");
			return;
		}
		if (concepts.getRoot() == null) {
			println( " -> no concepts/relations to save.");
			return;
		}
		String filename = workingPath + word;
		println("saving: " + filename);
		saveFile(filename);
	}
	
	private void commandWorker(LinkedList<String> words) {
		if (words.isEmpty()) {
			println(" -> With \"worker\" you can set the number of workers. Number is missing.");
			println(" -> default number is cores - 2");
			return;
		}
		String word = words.poll();
		int number = 0;
		try {
			number = Integer.valueOf(word);
		}
		catch (Exception e) {
			println(" -> " + word + " is not a valid integer-value.");
			return;
		}
		workerAmount = number;
		if (workerAmount < 1) { workerAmount = 1; }
		protocoll(" -> number of workers set to " + workerAmount);
		if (workerAmount == workers.size()) {
			return;
		}
		setWorkerOrder("stop");
		while (workerAmount < workers.size()) {
			String id = removeLastWorker();
			if (!id.equals("-1")) {
				protocoll(" -> removed worker " + id);
			}
			else {
				println(" -> problem while removing a worker.");
			}
		}
		while (workerAmount > workers.size()) {
			int id = addOneWorker();
			protocoll(" -> worker " + id + " has been created.");
		}
	}
	
	private void commandPath(LinkedList<String> words) {
		if (words.isEmpty()) {
			println(" -> the working-path is missing.");
			return;
		}
		String word = words.poll();
		word = word.trim();
		if (word.equals("")) {
			println(" -> the working-path is missing.");
		}
		if (!word.endsWith("\\")) {
			word += "\\";
		}
		workingPath = word;
		println(" -> the workingPath is now: " + workingPath);
	}
	
	/**
	 * @return the number existing concepts
	 */
	public long countConcepts() {
		return concepts.getSize();
	}
	
	public double getConceptValue(String conceptName) {
		Concept c = findConcept(conceptName);
		if (c != null) {
			return c.getValue();
		}
		return 0;
	}
	
	private LinkedList<String> getFile(String filename) {
		LinkedList<String> lines = new LinkedList<String>();
		FileReader file = null;
		try {
			file = new FileReader(filename);
			println(" -> opened file " + filename);
		}
		catch(IOException e) {
			println(e.getMessage());
			return lines;
		}
		BufferedReader reader = new BufferedReader(file);
		println(" -> reading file...");
		try {
			while(true) {
				String line = reader.readLine();
				if (line == null) { break; }
				lines.add(line);
			}
		}
		catch (IOException e) {
			println(" -> " + e.getMessage());
		}
		try {
			file.close();
		} catch (IOException e) {
			println(" -> " + e.getMessage());
		}
		try {
			reader.close();
		} catch (IOException e) {
			println(" -> " + e.getMessage());
		}
		println(" -> " + lines.size() + " lines read.");
		return lines;
	}
	
	private void saveFile(String filename) throws IOException {
		FileWriter file = null;
		try {
			file = new FileWriter(filename);
			println(" -> opened file " + filename);
		}
		catch(IOException e) {
			println(e.getMessage());
			return;
		}
		BufferedWriter writer = new BufferedWriter(file);
		// first create commands for concepts
		for (Concept c: concepts.traversePreOrder()) {
			writer.write(c.toCommand());
			writer.newLine();
		}
		// then create commands for relations
		for (Concept c: concepts.traversePreOrder()) {
			LinkedBlockingQueue<Relation> list = c.getRelationsLBQ();
			if (list != null) {
				for (Relation r: list) {
					writer.write(r.toCommand());
					writer.newLine();
				}
			}
		}
		writer.close();
		file.close();
		println(" -> file saved");
	}
	
	/**
	 * Adds a given text to the command queue.
	 * @param text
	 * @return
	 */
	public boolean addCommand(String text, String priority) {
		try {
			if (priority.equals("high")) {
				commands.addFirst(text);
			}
			else {
				commands.put(text);
			}
			processingCommands = true;
		}
		catch (Exception e) {
			println(" -> couldn't add the command");
			return false;
		}
		return true;
	}
	
	/**
	 * Returns the number of cores minus 2, that shall be used, but at least 1.
	 */
	private int getNumberOfWorkers() {
		/*int cores = Runtime.getRuntime().availableProcessors() - 2;
		if (cores <= 0) { cores = 1; }*/
		int cores = 1;
		return cores;
	}
	
	/**
	 * Create and start the workers. The workers are running, but still not working, until the <workerOrder> is set to do something.
	 * The workers are saved in the workers-list.
	 */
	private void createWorkers() {
		int countWorkers;
		if (workerAmount <= 0) { // default setting
			countWorkers = getNumberOfWorkers();
		}
		else {
			countWorkers = workerAmount;
		}
		while (workers.size() < countWorkers) {
			int id = addOneWorker();
			protocoll(" -> created and started worker with id " + id);
		}
	}
	
	private int addOneWorker() {
		Integer id = workers.size(); // id should be the position in the workers- and thread-lists
		Worker worker = new Worker(this, id.toString());
		Thread thread = new Thread(worker, id.toString());
		workers.add(worker);
		threads.add(thread);
		thread.start();
		return id;
	}
	
	/**
	 * Tries to remove the last worker and thread in the list. If not successfull then return -1.
	 * If successfull then it returns the id of the removed worker/thread.
	 * @return
	 */
	private String removeLastWorker() {
		int lastIndex = workers.size() - 1;
		if (lastIndex < 0) {
			return "-1";
		}
		Worker w = workers.get(lastIndex);
		Thread t = threads.get(lastIndex);
		w.setWorkerOrder("exit");
		while (!w.getStatus().equals("closed")) { }
		try {
			t.join();
		}
		catch (Exception e) { }
		// command("list threads");
		workers.remove(lastIndex);
		threads.remove(lastIndex);
		return w.getId();
	}
	
	private void setWorkerOrder(String order) {
		for (Worker w: workers) {
			// println(" -> start setting worker order \"" + order + "\" for worker " + w.getId());
			w.setWorkerOrder(order);
		}
	}
	
	/**
	 * Tries to put the given concept into the workingQueue. The method is regarding the
	 * repetitive-option of the concept.
	 * Priority = "high" the concept will be added at the head of the queue.
	 * Priority = "low" the concept will be added at the end of the queue.
	 * @param c
	 * @param priority "high" or "low"
	 */
	private void putIntoWorkingQueue(Concept c) {
		synchronized(this) {
			boolean added = false;	// has the concept been added?
			if (!c.isRepetitive()) {
				if (workingQueue.contains(c)) {
					return;
				}
			}
			while (!added) {
				try {
					if (c.getWorkingQueuePriority().equals("high")) {
						workingQueue.addFirst(c);
						added = true;
					}
					else {
						workingQueue.put(c);
						added = true;
					}
				}
				catch (InterruptedException e) {
					println(" -> problem while inserting in workingQueue");
				}
			}
		}
	}
	
	private void prepareWorkingQueue() {
		synchronized(this) {
			// put all Relations of starting Concepts into working Queue
			workingQueue.clear();
			for (Concept c: startConcepts) {	// go through all start-concepts
				try {
					if (c.isActive()) workingQueue.put(c);
				} catch (InterruptedException e) {
					println(" -> problems while inserting into workingQueue");
				}
			}
			if (workingQueue.isEmpty()) {
				println("    Warning: empty working queue");
			}
			if (startConcepts.isEmpty()) {
				println("    Warning: no entry-point");
			}
			reset();
		}
	}
		
	/**
	 * reset all values of concepts to their startValue
	 */
	private void reset() {
		// reset all values of the concepts
		ArrayList<Concept> con = concepts.traversePostOrder();
		if (con == null) {
			println("    reset: no concepts found to reset");
			return;
		}
		for (Concept c: con) {
			c.reset();
		}
	}
	
	public boolean isRunning() {
		return running;
	}
	
	@Override
	public void run() {
		String command = "";
		while(!command.equals("exit")) {
			if(!commands.isEmpty()) {
				try {
					command = commands.poll();
					//println(command);
					this.command(command);
					print("UniSim> ");
				}
				catch (Exception e) {
					//println(e.getMessage());
				}
			}
			else {
				processingCommands = false;
			}
			
		}
		println(" -> Matrix finished. Good Bye :(");
	}
	
	public void protocoll(String text) {
		if (protocollText) System.out.println(text);
	}
	
	public static void print(String text) {
		System.out.print(text);
	}
	
	public static void println(String text) {
		System.out.println(text);
	}
	
	public static void main(String[] args) throws InterruptedException {
		Matrix matrix = new Matrix();
		
		/*
		Concept geld = new Concept("Geld");
		geld.setStartValue(1000.0);
		geld.setValue(1000.0);
		geld.isStartConcept(true);
		
		Concept bank = new Concept("Bank");
		
		Relation r1 = new RelationAdd(geld, bank);
		r1.isDynamic(true);
		r1.shallMove(true);
		
		matrix.addConcept(geld);
		matrix.addConcept(bank);
		matrix.addRelation(r1);
		
		/*
		Concept geld = new Concept("Geld");
		geld.setStartValue(1000.0);
		geld.setValue(1000.0);
		geld.startingConcept = true;
		
		Concept bank = new Concept("Bank");
		
		Relation gebaPlus = new RelationPercent();
		gebaPlus.source = geld;
		gebaPlus.target = bank;
		gebaPlus.calculationSource = geld;
		gebaPlus.value = 100;
		
		Relation gebaMinus = new RelationPercent();
		gebaMinus.source = geld;
		gebaMinus.target = geld;
		gebaMinus.calculationSource = geld;
		gebaMinus.value = -100;
		
		matrix.addConcept(geld);
		matrix.addConcept(bank);
		matrix.addRelation(gebaPlus);
		matrix.addRelation(gebaMinus);
		
		*/
		/*
		print("Dein Geld: " + geld.getValue());
		print("In der Bank: " + bank.getValue());
				
		Thread matrixThread = new Thread(matrix, "matrixThread");
		matrixThread.start();
		matrixThread.join();
		
		print("Dein Geld: " + geld.getValue());
		print("In der Bank: " + bank.getValue());
		*/
		Thread matrixThread = new Thread(matrix, "matrixThread");
		matrixThread.start();
		java.util.Scanner userinput = new java.util.Scanner(System.in);
		String command = "";
		while (!command.equalsIgnoreCase("exit")) {
			command = userinput.nextLine();
			matrix.addCommand(command,"high");
		}
		userinput.close();
		try {
			matrixThread.join();
		}
		catch (Exception e) {}
	}
}
