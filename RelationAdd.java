package SimulationLibrary;

public class RelationAdd extends Relation{

	public RelationAdd(Concept source, Concept target) {
		super(source, target);
		setType("+");
	}
		
	@Override
	public void calculate() {
		super.calculate();
		setInterim(getValue());
	}
		
}
