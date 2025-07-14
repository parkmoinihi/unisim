package SimulationLibrary;

public class RelationMinus extends Relation {
	
	public RelationMinus(Concept source, Concept target) {
		super(source, target);
		setType("-");
	}
		
	@Override
	public void calculate() {
		super.calculate();
		setInterim(-getValue());
	}
}
