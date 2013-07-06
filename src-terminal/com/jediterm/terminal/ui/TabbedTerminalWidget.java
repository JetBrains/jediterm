package com.jediterm.terminal.ui;

import com.jediterm.terminal.RequestOrigin;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * @author traff
 */
public class TabbedTerminalWidget extends JTabbedPane implements TerminalWidget {
  private int myTabNumber = 1;

  private ResizePanelDelegate myResizePanelDelegate = null;

  @Override
  public TerminalSession createTerminalSession() {
    JediTermWidget terminal = new JediTermWidget();
    if (myResizePanelDelegate != null) {
      terminal.setResizePanelDelegate(myResizePanelDelegate);
    }
    String tabName = "Terminal " + myTabNumber++;
    addTab(tabName, null, terminal);

    setTabComponentAt(getTabCount() - 1, new TabComponent(this, terminal));
    setSelectedComponent(terminal);

    if (getTabCount() == 1) { //Init size
      setSize(terminal.getSize());
      myResizePanelDelegate.onPanelResize(getSize(), RequestOrigin.User);
    }

    return terminal;
  }

  private JPopupMenu createPopup(final JediTermWidget terminal) {
    JPopupMenu popupMenu = new JPopupMenu();

    JMenuItem close = new JMenuItem("Close");
    close.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent event) {
        terminal.close();
        remove(terminal);
      }
    });

    popupMenu.add(close);

    return popupMenu;
  }


  private class TabComponent extends JPanel {

    private TabComponent(final @NotNull JTabbedPane pane, final JediTermWidget terminal) {
      super(new FlowLayout(FlowLayout.LEFT, 0, 0));
      setOpaque(false);

      //make JLabel read titles from JTabbedPane
      JLabel label = new JLabel() {
        public String getText() {
          int i = pane.indexOfTabComponent(TabComponent.this);
          if (i != -1) {
            return pane.getTitleAt(i);
          }
          return null;
        }
      };

      //add more space between the label and the button
      label.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 5));

      label.addMouseListener(new MouseAdapter() {

        @Override
        public void mouseReleased(MouseEvent event) {
          handleMouse(event);
        }

        @Override
        public void mousePressed(MouseEvent event) {
          handleMouse(event);
        }

        private void handleMouse(MouseEvent event) {
          if (event.isPopupTrigger()) {
            JPopupMenu menu = createPopup(terminal);
            menu.show(event.getComponent(), event.getX(), event.getY());
          }
          else {
            pane.setSelectedComponent(terminal);
          }
        }
      });


      add(label);
    }
  }


  @Override
  public Component getComponent() {
    return this;
  }

  @Override
  public boolean canOpenSession() {
    return true;
  }

  @Override
  public void setResizePanelDelegate(ResizePanelDelegate resizePanelDelegate) {
    for (int i = 0; i < getTabCount(); i++) {
      getTerminalPanel(i).setResizePanelDelegate(resizePanelDelegate);
    }
    myResizePanelDelegate = resizePanelDelegate;
  }

  @Override
  public TerminalSession getCurrentSession() {
    return getTerminalPanel(getSelectedIndex());
  }

  private TerminalSession getTerminalPanel(int index) {
    return (TerminalSession)getComponentAt(index);
  }
}
