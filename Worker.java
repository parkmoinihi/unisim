package SimulationLibrary;

import java.util.concurrent.LinkedBlockingDeque;

public class Worker implements Runnable {

	private String id;
	private String status ="stopped";
	private String statusInfo = "waiting for orders";
	
	Matrix matrix;	// the parent-matrix, that is the top level of the simulation
	
	private String workerOrder = "stop"; // the workers checks this property to know when stop or pause or to proceed, will be set by matrix
	
	public Worker(Matrix matrix, String id) {
		this.matrix = matrix;
		this.id = id;
	}

	public String getId() {
		return id;
	}
	
	public String getStatus() {
		return status;
	}
	
	public String getInfo() {
		String s = "Worker " + id + ": order=\"" + workerOrder + "\", " + status + " - " + statusInfo;
		return s;
	}
	
	/**
	 * Giving a command(an order) to the worker. 
	 * @param order
	 */
	public void setWorkerOrder(String order) {
		workerOrder = order;
		// print(" -> set order for worker " + id + " to " + order);
	}
	
	@Override
	public void run() {
		while (!workerOrder.equals("exit")) {
			while (matrix.workingQueue.isEmpty()) {
				statusInfo = "no elements in workingQueue"; 
				if (workerOrder.equals("exit")) { 
					status = "closed";
					break; 
				}
				if (workerOrder.equals("run")) { status = "running"; }
				else if (workerOrder.equals("stop")) { status = "stopped"; }
				else { status = workerOrder; }
			}
			while (!matrix.workingQueue.isEmpty()) {
				statusInfo = "found elements in Queue";
				if (workerOrder.equals("exit")) {
					status = "closed";
					break; 
				}
				else if (workerOrder.equals("run")) {
					status = "running";
					Concept c;
					try {
						c = matrix.workingQueue.poll();
						// if the concept is a stopping concept, then give the order to the matrix
						if (c.isStopConcept()) {
							matrix.processingCommands = true;	// causes this worker will not work, so the stop-command takes immediate effect in single core
							matrix.commands.addFirst("stop");
							workerOrder = "stop";
							break;
						}
						if (c.isActive() && !c.command.isEmpty()) { // activate command
							String s = c.command.getFirst();
							c.command.add(s);	// the command will be queued again
							matrix.processingCommands = true;
							matrix.commands.add(s);
							while (matrix.processingCommands) {}
						}
						for (Relation r: c.getRelationsLBQ()) {		// go through all relations of current concept
							if (r.isActive()) {		// if relation is active
								r.calculate();
							}
						}
						for (Relation r: c.getRelationsLBQ()) {	// go through all relations of current concept
							if (r.isActive()) {			// if relation is active
								r.addInterim();
								c.raiseNumberActiveRelations();
								// add target-concepts to the workingQueue
								Concept target = r.getTarget();
								if (target.isActive()) {
									if ((r.getInterim() == 0 && target.comesIntoQueueWhenZero()) || r.getInterim() != 0) {
										if (target.isRepetitive()) { // target is repetitive
											try {
												if (target.getWorkingQueuePriority().equals("high")) {
													matrix.workingQueue.addFirst(target);
												}
												else {
													matrix.workingQueue.put(target);
												}
											} catch (InterruptedException e) {
												print(" -> worker-problem while inserting in workingQueue");
											}
										}
										else if (!matrix.workingQueue.contains(r.getTarget())) { // not repetitive and target not in queue
											try {
												if (target.getWorkingQueuePriority().equals("high")) {
													matrix.workingQueue.addFirst(target);
												}
												else {
													matrix.workingQueue.put(target);
												}
											} catch (InterruptedException e) {
												print(" -> worker-problem while inserting in workingQueue");
											}
										}
									}
								}
							}
						}
						c.raiseProcessedCounter();
						if (c.isResetOne()) {
							if (c.getNumberActiveRelations() > 0) {
								c.resetThreshold();
							}
						}
					}
					catch (Exception e) {
						// print(" -> worker " + id + " wasn't able to get a concept");
						break;
					}
				}
				else if(workerOrder.equals("stop")) {
					status = "stopped";
				}
				else {
					status = workerOrder;
				}
			}
		}
		print(" -> Worker " + id + " finshed.");
	}
	
	public void print(String text) {
		System.out.println(text);
	}
	
}
