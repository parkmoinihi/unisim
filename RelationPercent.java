package SimulationLibrary;

public class RelationPercent extends Relation {
	
	public RelationPercent(Concept source, Concept target) {
		super(source, target);
		setType("%");
	}
	
	@Override
	public void calculate() {
		super.calculate();
		setInterim(getCalculationSource().getValue() / 100 * getValue());
	}
}
