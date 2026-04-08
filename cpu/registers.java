
public class registers {
    private int A, B, C, D, E, H, L;
    private int F;

    private int PC;
    private int SP;

    public static final int FLAG_Z = 0x80;
    public static final int FLAG_N = 0x40;
    public static final int FLAG_H = 0x20;
    public static final int FLAG_C = 0x10;

    public int getA(){
        return A & 0xFF;
    }

    public int getB(){
        return B & 0xFF;
    }

    public int getC(){
        return C & 0xFF;
    }

    public int getD(){
        return D & 0xFF;
    }

    public int getE(){
        return E & 0xFF;
    }
    
    public int getF(){
        return F & 0xF0;
    }

    public int getL(){
        return L & 0xFF;
    }

    public int getH(){
        return H & 0xFF;
    }

    public void setA(int v){
        A = v & 0xFF;
    }

    public void setB(int v){
        B = v & 0xFF;
    }

    public void setC(int v){
        C = v & 0xFF;
    }

    public void setD(int v){
        D = v & 0xFF;
    }

    public void setE(int v){
        E = v & 0xFF;
    }

    public void setF(int v){
        F = v & 0xF0;
    }

    public void setH(int v){
        H = v & 0xFF;
    }

    public void setL(int v){
        L = v & 0xFF;
    }

    public int getAF(){
        return (getA() << 8) | getF();
    }

    public int getBC(){
        return (getB() << 8) | getC();
    }

    public int getDE(){
        return (getD() << 8) | getE();
    }

    public int getHL(){
        return (getH() << 8) | getL();
    }

    public int getSP(){
        return SP & 0xFFFF;
    }

    public int getPC(){
        return PC & 0xFFFF;
    }

    public void setAF(int v) {
        setA((v >> 8) & 0xFF);
        setF(v & 0xFF);
    }

    public void setBC(int v) {
        setB((v >> 8) & 0xFF);
        setC(v & 0xFF);
    }

    public void setDE(int v) {
        setD((v >> 8) & 0xFF);
        setE(v & 0xFF);
    }
    
    public void setHL(int v) {
        setH((v >> 8) & 0xFF);
        setL(v & 0xFF);
    }


    public void setSP(int v) {
        SP = v & 0xFFFF;
    }

    public void setPC(int v) {
        PC = v & 0xFFFF;
    }
    
    public boolean isFlagSet(int flag) {
        return (F & flag) != 0;
    }

    public void setFlag(int flag) {
        F |= flag;
        F &= 0xF0;
    }

    public void clearFlag(int flag) {
        F &= ~flag;
        F &= 0xF0;
    }

    public void setFlagIf(int flag, boolean condition){
        if (condition) {setFlag(flag);} 
        else {clearFlag(flag);}
    }

    public boolean isZero() { return isFlagSet(FLAG_Z);}
    public boolean isSubtract() { return isFlagSet(FLAG_N);}
    public boolean isHalfCarry() { return isFlagSet(FLAG_H);}
    public boolean isCarry() { return isFlagSet(FLAG_C); }

    public int incPC() { 
        int old = getPC();
        setPC(old+1);
        return old;
    }

    public void reset(){
        setAF(0x01B0);
        setBC(0x0013);
        setDE(0x00D8);
        setHL(0x014D);
        SP = 0xFFFE;
        PC = 0x0100;
    }

    public String toString(){
        return String.format(
            "AF=%04X  BC=%04X  DE=%04X  HL=%04X  SP=%04X  PC=%04X  Flags=[%s%s%s%s]",
            getAF(), getBC(), getDE(), getHL(), getSP(), getPC(),
            isZero()      ? "Z" : "-",
            isSubtract()  ? "N" : "-",
            isHalfCarry() ? "H" : "-",
            isCarry()     ? "C" : "-"
        );
    }
}