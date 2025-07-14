package SimulationLibrary;

public class RelationDivide extends Relation {
	
	public RelationDivide(Concept source, Concept target) {
		super(source, target);
		setType("/");
	}
		
	@Override
	public void calculate() {
		super.calculate();
		setInterim(getCalculationSource().getValue() / getValue());
	}
}
