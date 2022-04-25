package analyzer.visitors;

import analyzer.SemantiqueError;
import analyzer.ast.*;
//import jdk.tools.jaotc.binformat.SymbolTable;

import javax.lang.model.element.VariableElement;
import javax.xml.crypto.Data;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created: 19-01-10
 * Last Changed: 22-01-29
 * Author: Esther Guerrier
 * Modified by: Hakim Mektoub
 * <p>
 * Description: Ce visiteur explorer l'AST est renvois des erreur lorqu'une erreur sémantique est détecté.
 */

public class SemantiqueVisitor implements ParserVisitor {

    private final PrintWriter writer;

    private HashMap<String, VarType> symbolTable = new HashMap<>(); // mapping variable -> type

    // variable pour les metrics
    private int VAR = 0;
    private int WHILE = 0;
    private int IF = 0;
    private int FOR = 0;
    private int OP = 0;
    private boolean error = false;

    public SemantiqueVisitor(PrintWriter writer) {
        this.writer = writer;
    }

    //Vous pouvez utilisez cette fonction pour imprimer vos erreurs.
    private void print(final String msg) {
        if (!error) {
            writer.print(msg);
            error = true;
        }
    }

    /*
    Le Visiteur doit lancer des erreurs lorsqu'un situation arrive.

    regardez l'énoncé ou les tests pour voir le message à afficher et dans quelle situation.
    Lorsque vous voulez afficher une erreur, utilisez la méthode print implémentée ci-dessous.
    Tous vos tests doivent passer!!

     */

    @Override
    public Object visit(SimpleNode node, Object data) {
        node.childrenAccept(this, data);
        return data;
    }

    @Override
    public Object visit(ASTProgram node, Object data) {
        node.childrenAccept(this, data);
        print(String.format("{VAR:%d, WHILE:%d, IF:%d, FOR:%d, OP:%d}", this.VAR, this.WHILE, this.IF, this.FOR, this.OP));
        return data;
    }

    /*
    Ici se retrouve les noeuds servant à déclarer une variable.
    Certaines doivent enregistrer les variables avec leur type dans la table symbolique.
     */
    @Override
    public Object visit(ASTDeclaration node, Object data) {
        node.childrenAccept(this, data);
        //VAR++;
        return data;
    }

    @Override
    public Object visit(ASTNormalDeclaration node, Object data) {
        //node.childrenAccept(this, data);

        String name = ((ASTIdentifier) node.jjtGetChild(0)).getValue();
        if (symbolTable.containsKey(name)) {
            print("Invalid declaration... variable "+ name +" already exists");
        }
        else {
            this.VAR++;
            if (node.getValue().equals("num")) {
                symbolTable.put(name, VarType.num);
            }
            else if (node.getValue().equals("bool")) {
                symbolTable.put(name, VarType.bool);
            }
            else if (node.getValue().equals("real")) {
                symbolTable.put(name, VarType.real);
            }
        }

        return data;
    }

    @Override
    public Object visit(ASTListDeclaration node, Object data) {
        //node.childrenAccept(this, data);

        String name = ((ASTIdentifier) node.jjtGetChild(0)).getValue();
        if (symbolTable.containsKey(name)) {
            print("Invalid declaration... variable "+ name +" already exists");
        }
        else {
            this.VAR++;
            if (node.getValue().equals("listnum")) {
                symbolTable.put(name, VarType.listnum);
            }
            else if (node.getValue().equals("listbool")) {
                symbolTable.put(name, VarType.listbool);
            }
            else if (node.getValue().equals("listreal")) {
                symbolTable.put(name, VarType.listreal);
            }
        }

        return data;
    }

    @Override
    public Object visit(ASTBlock node, Object data) {
        node.childrenAccept(this, data);
        return data;
    }


    @Override
    public Object visit(ASTStmt node, Object data) {
        node.childrenAccept(this, data);
        return data;
    }

    /*
     * Il faut vérifier que le type déclaré à gauche soit compatible avec la liste utilisée à droite. N'oubliez pas
     * de vérifier que les variables existent.
     */

    @Override
    public Object visit(ASTForEachStmt node, Object data) {
        //node.childrenAccept(this, data);

        this.FOR++;
        this.OP++;
        DataStruct firstStruct = new DataStruct();
        DataStruct secondStruct = new DataStruct();
        node.jjtGetChild(0).jjtAccept(this, firstStruct);
        node.jjtGetChild(1).jjtAccept(this, secondStruct);

        if (secondStruct.type == VarType.listnum) {
            print("Array type listnum is incompatible with declared variable of type bool...");
        }
        else if (secondStruct.type == VarType.listbool) {
            print("Array type listbool is incompatible with declared variable of type num...");
        }
        else if (secondStruct.type == VarType.listreal) {
            print("Array type listreal is incompatible with declared variable of type num...");
        }
        else {
            node.jjtGetChild(2).jjtAccept(this, secondStruct);
            print("Array type is required here...");
        }

        return data;
    }

    /*
    Ici faites attention!! Lisez la grammaire, c'est votre meilleur ami :)
     */
    @Override
    public Object visit(ASTForStmt node, Object data) {
        //node.childrenAccept(this, data);

        this.FOR++;
        this.OP++;
        DataStruct firstStruct = new DataStruct();
        DataStruct secondStruct = new DataStruct();
        node.jjtGetChild(0).jjtAccept(this, firstStruct);
        node.jjtGetChild(1).jjtAccept(this, secondStruct);

        if (secondStruct.type == VarType.bool) {
            this.OP++;
            return data;
        }
        else if (secondStruct.type == VarType.num) {
            print("Invalid type in condition");
        }
        else {
            node.jjtGetChild(2).jjtAccept(this, secondStruct);
        }

        return data;
    }

    /*
    Méthode recommandée à implémenter puisque vous remarquerez que quelques fonctions ont exactement le même code! N'oubliez
    -pas que la qualité du code est évalué :)
     */
    private void callChildenCond(SimpleNode node) {
        DataStruct temp = new DataStruct();
        node.jjtGetChild(0).jjtAccept(this, temp);
        for (int i = 1; i < node.jjtGetNumChildren(); i++) {
            node.jjtGetChild(i).jjtAccept(this, temp);
        }
    }

    /*
    les structures conditionnelle doivent vérifier que leur expression de condition est de type booléenne
    On doit aussi compter les conditions dans les variables IF et WHILE
     */
    @Override
    public Object visit(ASTIfStmt node, Object data) {
        //node.childrenAccept(this, data);

        this.IF++;
        DataStruct struct = new DataStruct();
        node.jjtGetChild(0).jjtAccept(this, struct);
        if (struct.type == VarType.bool) {
            node.jjtGetChild(1).jjtAccept(this, data);
            if (node.jjtGetNumChildren() > 2) {
                node.jjtGetChild(2).jjtAccept(this, data);
            }
        }
        else {
            print("Invalid type in condition");
        }

        return data;
    }

    @Override
    public Object visit(ASTWhileStmt node, Object data) {
        //node.childrenAccept(this, data);

        this.WHILE++;
        DataStruct struct = new DataStruct();
        node.jjtGetChild(0).jjtAccept(this, struct);
        if (struct.type == VarType.bool) {
            node.jjtGetChild(1).jjtAccept(this, data);
        }
        else {
            print("Invalid type in condition");
        }

        return data;
    }

    /*
    On doit vérifier que le type de la variable est compatible avec celui de l'expression.
    La variable doit etre déclarée.
     */
    @Override
    public Object visit(ASTAssignStmt node, Object data) {
        //node.childrenAccept(this, data);

        String name = ((ASTIdentifier) node.jjtGetChild(0)).getValue();
        DataStruct firstStruct = new DataStruct();
        DataStruct secondStruct = new DataStruct();
        if (node.jjtGetNumChildren() > 1) {
            node.jjtGetChild(0).jjtAccept(this, firstStruct);
            node.jjtGetChild(1).jjtAccept(this, secondStruct);
            if (firstStruct.type != secondStruct.type) {
                print("Invalid type in assignation of Identifier " + name + "... was expecting " + firstStruct.type.toString() + " but got " + (secondStruct.type.toString()));
            }
        }
        else {
            node.childrenAccept(this, data);
        }

        return data;
    }

    @Override
    public Object visit(ASTExpr node, Object data) {
        node.childrenAccept(this, data);
        return data;
    }

    @Override
    public Object visit(ASTCompExpr node, Object data) {
        /*attention, ce noeud est plus complexe que les autres.
        si il n'a qu'un seul enfant, le noeud a pour type le type de son enfant.

        si il a plus d'un enfant, alors ils s'agit d'une comparaison. il a donc pour type "bool".

        de plus, il n'est pas acceptable de faire des comparaisons de booleen avec les opérateur < > <= >=.
        les opérateurs == et != peuvent être utilisé pour les nombres, les réels et les booléens, mais il faut que le type soit le même
        des deux côté de l'égalité/l'inégalité.
        */

        this.OP += node.jjtGetNumChildren() - 1;
        if(node.jjtGetNumChildren() > 1) {
            DataStruct struct = new DataStruct();
            node.jjtGetChild(0).jjtAccept(this, struct);
            VarType variable = struct.type;
            for (int i = 1; i < node.jjtGetNumChildren(); i++) {
                node.jjtGetChild(i).jjtAccept(this, struct);
                if (struct.type != variable|| (!(node.getValue().equals("!=") || node.getValue().equals("==")) && (struct.type != VarType.num))) {
                    print("Invalid type in expression");
                    break;
                }
            }
            ((DataStruct) data).type = VarType.bool;
        }
        else {
            node.childrenAccept(this, data);
        }

        return data;
    }

    private void callChildren(SimpleNode node, Object data, VarType validType) {

    }

    /*
    opérateur binaire
    si il n'y a qu'un enfant, aucune vérification à faire.
    par exemple, un AddExpr peut retourné le type "Bool" à condition de n'avoir qu'un seul enfant.
    Sinon, il faut s'assurer que les types des valeurs sont les mêmes des deux cotés de l'opération
     */
    @Override
    public Object visit(ASTAddExpr node, Object data) {
        //node.childrenAccept(this, data);
        //callChildren(node, data, VarType.num);

        this.OP += node.jjtGetNumChildren() - 1;
        DataStruct struct = new DataStruct();
        if (node.jjtGetNumChildren() > 1) {
            for (int i = 0; i < node.jjtGetNumChildren(); i++) {
                node.jjtGetChild(i).jjtAccept(this, struct);
                if (struct.type != VarType.num) {
                    print("Invalid type in expression");
                    break;
                }
            }
            ((DataStruct) data).type = VarType.num;
        }
        else {
            node.childrenAccept(this, data);
        }

        return data;
    }

    @Override
    public Object visit(ASTMulExpr node, Object data) {
        //node.childrenAccept(this, data);
        //callChildren(node, data, VarType.num);

        this.OP += node.jjtGetNumChildren() - 1;
        DataStruct struct = new DataStruct();
        if (node.jjtGetNumChildren() > 1) {
            for (int i = 0; i < node.jjtGetNumChildren(); i++) {
                node.jjtGetChild(i).jjtAccept(this, struct);
                if (struct.type != VarType.num) {
                    print("Invalid type in expression");
                    break;
                }
            }
            ((DataStruct) data).type = VarType.num;
        }
        else {
            node.childrenAccept(this, data);
        }

        return data;
    }

    @Override
    public Object visit(ASTBoolExpr node, Object data) {
        //node.childrenAccept(this, data);
        //callChildren(node, data, VarType.bool);

        this.OP += node.jjtGetNumChildren() - 1;
        DataStruct struct = new DataStruct();
        if (node.jjtGetNumChildren() > 1) {
            for (int i = 0; i < node.jjtGetNumChildren(); i++) {
                node.jjtGetChild(i).jjtAccept(this, struct);
                if (struct.type != VarType.bool) {
                    print("Invalid type in expression");
                    break;
                }
            }
            ((DataStruct) data).type = VarType.bool;
        }
        else {
            node.childrenAccept(this, data);
        }

        return data;
    }

    /*
    opérateur unaire
    les opérateur unaire ont toujours un seul enfant.

    Cependant, ASTNotExpr et ASTUnaExpr ont la fonction "getOps()" qui retourne un vecteur contenant l'image (représentation str)
    de chaque token associé au noeud.

    Il est utile de vérifier la longueur de ce vecteur pour savoir si une opérande est présente.

    si il n'y a pas d'opérande, ne rien faire.
    si il y a une (ou plus) opérande, ils faut vérifier le type.

    */
    @Override
    public Object visit(ASTNotExpr node, Object data) {
        this.OP += node.getOps().size();
        node.childrenAccept(this, data);

        if (VarType.bool != ((DataStruct) data).type && node.getOps().size() > 0) {
            print("Invalid type in expression");
        }
        if (node.getOps().size() > 0)  {
            ((DataStruct) data).type = VarType.bool;
        }

        return data;
    }

    @Override
    public Object visit(ASTUnaExpr node, Object data) {
        this.OP += node.getOps().size();
        node.childrenAccept(this, data);

        if (VarType.num != ((DataStruct) data).type && node.getOps().size() > 0) {
            print("Invalid type in expression");
        }
        if (node.getOps().size() > 0) {
            ((DataStruct)data).type = VarType.num;
        }
        return data;
    }

    /*
    les noeud ASTIdentifier aillant comme parent "GenValue" doivent vérifier leur type et vérifier leur existence.

    Ont peut envoyé une information a un enfant avec le 2e paramètre de jjtAccept ou childrenAccept.
     */
    @Override
    public Object visit(ASTGenValue node, Object data) {
        node.childrenAccept(this, data);
        return data;
    }


    @Override
    public Object visit(ASTBoolValue node, Object data) {
        //node.childrenAccept(this, data);
        ((DataStruct) data).type = VarType.bool;
        return data;
    }

    @Override
    public Object visit(ASTIdentifier node, Object data) {
        //node.childrenAccept(this, data);

        if(!symbolTable.containsKey(node.getValue())) {
            print("Invalid use of undefined Identifier " + node.getValue());
            ((DataStruct)data).type = VarType.undefined;
        }
        else {
            ((DataStruct) data).type = symbolTable.get(node.getValue());
        }

        return data;
    }

    @Override
    public Object visit(ASTIntValue node, Object data) {
        //node.childrenAccept(this, data);
        ((DataStruct) data).type = VarType.num;
        return data;
    }

    @Override
    public Object visit(ASTRealValue node, Object data) {
        //node.childrenAccept(this, data);
        ((DataStruct) data).type = VarType.real;
        return data;
    }

    //des outils pour vous simplifier la vie et vous enligner dans le travail
    public enum VarType {
        bool,
        num,
        real,
        listnum,
        listbool,
        listreal,
        undefined
    }

    private class DataStruct {
        public VarType type;

        public DataStruct() {
        }

        public DataStruct(VarType p_type) {
            type = p_type;
        }
    }
}
