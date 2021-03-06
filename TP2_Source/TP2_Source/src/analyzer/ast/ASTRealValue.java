package analyzer.ast;

public class ASTRealValue extends SimpleNode {
    public ASTRealValue(int id) {
        super(id);
    }

    public ASTRealValue(Parser p, int id) {
        super(p, id);
    }


    /** Accept the visitor. **/
    public Object jjtAccept(ParserVisitor visitor, Object data) {
        return visitor.visit(this, data);
    }

    // PLB
    private double m_value = 0.0;
    public void setValue(double v) { m_value = v; }
    public double getValue() { return m_value; }
}