package rails.ui.swing;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Stroke;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.border.AbstractBorder;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rails.common.parser.Config;
import rails.game.GameManager;
import rails.game.Player;
import rails.game.PublicCompany;
import rails.game.Round;
import rails.game.state.BooleanState;
import rails.game.state.Observable;
import rails.game.state.Observer;
import rails.ui.swing.elements.ClickField;

public abstract class GridPanel extends JPanel
implements ActionListener, KeyListener {

    // TODO: Check if adding the field is compatible
    private static final long serialVersionUID = 1L;
    
    protected static final int NARROW_GAP = 1;
    protected static final int WIDE_GAP = 3;
    protected static final int WIDE_LEFT = 1;
    protected static final int WIDE_RIGHT = 2;
    protected static final int WIDE_TOP = 4;
    protected static final int WIDE_BOTTOM = 8;

    protected JPanel gridPanel;
    protected JFrame parentFrame;

    protected GridBagLayout gb;
    protected GridBagConstraints gbc;

    protected static Color buttonHighlight = new Color(255, 160, 80);

    protected int np;
    protected Player[] players;
    protected int nc;
    protected PublicCompany[] companies;
    protected Round round;
    protected PublicCompany c;
    protected JComponent f;

    protected List<Observer> observers = new ArrayList<Observer>();

    /** 2D-array of fields to enable show/hide per row or column */
    protected JComponent[][] fields;
    /** Array of Observer objects to set row visibility */
    protected RowVisibility[] rowVisibilityObservers;

    protected List<JMenuItem> menuItemsToReset = new ArrayList<JMenuItem>();

    protected static Logger log =
        LoggerFactory.getLogger(GridPanel.class);

    private JComponent highlightedComp = null;
    protected Color tableBorderColor;
    protected Color cellOutlineColor;
    protected Color highlightedBorderColor;
    
    public GridPanel() {
        //initialize border colors according to the configuration
        if ("enabled".equals(Config.get("gridPanel.tableBorders"))) {
            tableBorderColor = Color.DARK_GRAY;
            cellOutlineColor = Color.GRAY;
            highlightedBorderColor = Color.RED;
        } else {
            tableBorderColor = getBackground();
            cellOutlineColor = getBackground();
            highlightedBorderColor = Color.RED;
        }
    }

    public void redisplay() {
        revalidate();
    }

    protected void deRegisterObservers() {
        log.debug("Deregistering observers");
        for (Observer o : observers) {
            o.getObservable().removeObserver(o);
        }
    }

    protected void addField(JComponent comp, int x, int y, int width, int height,
            int wideGapPositions) {
        addField (comp, x, y, width, height, wideGapPositions, true);
    }

    protected void addField(JComponent comp, int x, int y, int width, int height,
            int wideGapPositions, boolean visible) {

        gbc.gridx = x;
        gbc.gridy = y;
        gbc.gridwidth = width;
        gbc.gridheight = height;
        gbc.weightx = gbc.weighty = 0.5;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(0,0,0,0);

        //special handling of clickfields as their compound border does not fit
        //to this field border logic
        if ((comp instanceof ClickField)) {
            comp.setBorder(BorderFactory.createLineBorder(new Color(0,0,0,0),1));
        }

        int padTop, padLeft, padBottom, padRight;
        padTop = (wideGapPositions & WIDE_TOP) > 0 ? WIDE_GAP - NARROW_GAP : 0;
        padLeft = (wideGapPositions & WIDE_LEFT) > 0 ? WIDE_GAP - NARROW_GAP : 0;
        padBottom =
                (wideGapPositions & WIDE_BOTTOM) > 0 ? WIDE_GAP - NARROW_GAP : 0;
        padRight = (wideGapPositions & WIDE_RIGHT) > 0 ? WIDE_GAP - NARROW_GAP : 0;

        //set field borders
        //- inner border: the field's native border
        //- outline border: the field's outline (in narrow_gap thickness)
        //- outer border: grid table lines (in wide_gap - narrow_gap thickness)
        
        comp.setBorder(new FieldBorder(comp.getBorder(),
                new DynamicSymmetricBorder(cellOutlineColor,NARROW_GAP),
                new DynamicAsymmetricBorder(tableBorderColor,padTop,padLeft,padBottom,padRight)));

        gridPanel.add(comp, gbc);

        if (comp instanceof Observer) {
            observers.add((Observer) comp);
        }

        if (fields != null && fields[x][y] == null) fields[x][y] = comp;
        comp.setVisible(visible);
    }
    
    /**
     * highlights given component by altering its border's attributes
     * If another component had been highlighted before, it's highlighting is first
     * undone before highlighting the new given component.
     */
    protected void setHighlight(JComponent comp,boolean isToBeHighlighted) {
        //quit if nothing is to be done
        if (isToBeHighlighted && comp == highlightedComp) return;
        removeHighlight();
        if (isToBeHighlighted) {
            if (comp.getBorder() instanceof FieldBorder) {
                FieldBorder fb = (FieldBorder)comp.getBorder();
                fb.setHighlight(isToBeHighlighted);
                comp.repaint();
            }
            highlightedComp = comp;
        }
    }
    
    protected void removeHighlight() {
        if (highlightedComp == null) return;
        if (highlightedComp.getBorder() instanceof FieldBorder) {
            FieldBorder fb = (FieldBorder)highlightedComp.getBorder();
            fb.setHighlight(false);
            highlightedComp.repaint();
        }
        highlightedComp = null;
    }
    
    public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_F1) {
            HelpWindow.displayHelp(GameManager.getInstance().getHelp());
            e.consume();
        }
    }

    public void keyReleased(KeyEvent e) {}

    public void keyTyped(KeyEvent e) {}

    public void setRowVisibility (int rowIndex, boolean value) {


        for (int j=0; j < fields.length; j++) {
            if (fields[j][rowIndex] != null) {
                fields[j][rowIndex].setVisible(value);
                // TODO: Check if this does not cause any issues
                //List<JComponent> dependents;
//                if (fields[j][rowIndex] instanceof Field
//                        && (dependents = ((Field)fields[j][rowIndex]).getDependents()) != null) {
//                    for (JComponent dependent : dependents) {
//                        dependent.setVisible(value);
//                    }
//                }
            }
        }
    }

    
    /**
     * An observer object that receives the updates 
     * if the Company is closed
     * 
     * TODO: It is unclear to me what the reverseValue really does?
     */
    public class RowVisibility implements Observer {

        private final GridPanel parent;
        private final int rowIndex;
        private final BooleanState observable;

        public RowVisibility (GridPanel parent, int rowIndex, BooleanState observable, boolean reverseValue) {
            this.parent = parent;
            this.rowIndex = rowIndex;
            this.observable = observable;
            // TODO: This was the previous setup
//            lastValue = ((BooleanState)observable).value() != reverseValue;
            
        }

        public boolean lastValue () {
            return observable.value();
        }

        public void update(String text) {
            parent.setRowVisibility(rowIndex, lastValue());
        }
        
        public Observable getObservable() {
            return observable;
        }

    }

    /**
     * Wrapper for three level compound borders and directly accessing border constituents 
     * @author Frederick Weld
     *
     */
    private class FieldBorder extends CompoundBorder {
        private static final long serialVersionUID = 1L;
        Border nativeInnerBorder;
        DynamicAsymmetricBorder highlightedInnerBorder;
        DynamicSymmetricBorder outlineBorder;
        DynamicAsymmetricBorder outerBorder;
        public FieldBorder(Border innerBorder,DynamicSymmetricBorder outlineBorder,DynamicAsymmetricBorder outerBorder) {
            super(new CompoundBorder(outerBorder,outlineBorder),innerBorder);
            this.nativeInnerBorder = innerBorder;
            this.outlineBorder = outlineBorder;
            this.outerBorder = outerBorder;
            this.highlightedInnerBorder = new DynamicAsymmetricBorder(
                    highlightedBorderColor,
                    nativeInnerBorder.getBorderInsets(null).top,
                    nativeInnerBorder.getBorderInsets(null).left,
                    nativeInnerBorder.getBorderInsets(null).bottom,
                    nativeInnerBorder.getBorderInsets(null).right);
        }
        public void setHighlight(boolean isToBeHighlighted) {
            outlineBorder.setHighlight(isToBeHighlighted);
            this.insideBorder = isToBeHighlighted ? 
                    highlightedInnerBorder : nativeInnerBorder;
        }
    }

    /**
     * A line border providing methods for changing the look 
     * @author Frederick Weld
     *
     */
    private class DynamicSymmetricBorder extends AbstractBorder {
        private static final long serialVersionUID = 1L;
        private int thickness;
        private Color borderColor;
        private boolean isHighlighted = false;
        public DynamicSymmetricBorder (Color borderColor,int thickness) {
            this.thickness = thickness;
            this.borderColor = borderColor;
        }
        public void setHighlight(boolean isToBeHighlighted) {
            if (isHighlighted != isToBeHighlighted) {
                isHighlighted = isToBeHighlighted;
            }
        }
        
        public void paintBorder(Component c,Graphics g, int x, int y, int width,int height) {
            Graphics2D g2d = (Graphics2D)g;
            Stroke oldStroke = g2d.getStroke();
            g2d.setStroke(new BasicStroke(thickness));
            if (isHighlighted) {
                g2d.setColor(highlightedBorderColor);
            } else {
                g2d.setColor(borderColor);
            }
            g2d.drawRect(x, y, width-1, height-1);
            g2d.setStroke(oldStroke);
        }

        public Insets getBorderInsets (Component c) {
            return new Insets(thickness,thickness,thickness,thickness);
        }

        public boolean isBorderOpaque() { 
            return true; 
        }
    }
    /**
     * An asymmetric line border providing methods for changing the look 
     * @author Frederick Weld
     *
     */
    private class DynamicAsymmetricBorder extends AbstractBorder {
        private static final long serialVersionUID = 1L;
        private int padTop, padLeft, padBottom, padRight;
        private Color borderColor;
        public DynamicAsymmetricBorder (Color borderColor,int padTop, int padLeft, int padBottom, int padRight) {
            this.padTop = padTop;
            this.padLeft = padLeft;
            this.padBottom = padBottom;
            this.padRight = padRight;
            this.borderColor = borderColor;
        }
        public void paintBorder(Component c,Graphics g, int x, int y, int width,int height) {
            Graphics2D g2d = (Graphics2D)g;
            g2d.setColor(borderColor);
            Stroke oldStroke = g2d.getStroke();
            if (padTop > 0) {
                g2d.setStroke(new BasicStroke(padTop));
                g2d.fillRect(x, y, width, padTop);
            }
            if (padLeft > 0) {
                g2d.setStroke(new BasicStroke(padLeft));
                g2d.fillRect(x, y, padLeft, height);
            }
            if (padBottom > 0) {
                g2d.setStroke(new BasicStroke(padBottom));
                g2d.fillRect(x, y+height-padBottom, width, padBottom);
            }
            if (padRight > 0) {
                g2d.setStroke(new BasicStroke(padRight));
                g2d.fillRect(x+width-padRight, y, padRight, height);
            }
            g2d.setStroke(oldStroke);
        }

        public Insets getBorderInsets (Component c) {
            return new Insets(padTop,padLeft,padBottom,padRight);
        }

        public boolean isBorderOpaque() { 
            return true; 
        }
    }
}
