package SimulationLibrary;

public class RelationMultiply extends Relation {
	
	public RelationMultiply(Concept source, Concept target) {
		super(source, target);
		setType("*");
	}
	
	@Override
	public void calculate() {
		super.calculate();
		setInterim(getCalculationSource().getValue() * getValue());
	}
}
