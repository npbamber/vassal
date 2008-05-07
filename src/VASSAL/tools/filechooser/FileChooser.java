/*
 * $Id$
 *
 * Copyright (c) 2006-2008 by Joel Uckelman
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public
 * License (LGPL) as published by the Free Software Foundation.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Library General Public License for more details.
 *
 * You should have received a copy of the GNU Library General Public
 * License along with this library; if not, copies are available
 * at http://www.opensource.org.
 */
package VASSAL.tools.filechooser;

import java.awt.Component;
import java.awt.Dialog;
import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.Window;
import java.io.File;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import VASSAL.Info;
import VASSAL.configure.DirectoryConfigurer;

/**
 * FileChooser provides a wrapper for {@link javax.swing.JFileChooser} and
 * {@link java.awt.FileDialog}, selecting whichever is preferred on the
 * user's OS. <code>FileChooser</code>'s methods mirror those of
 * <code>JFileChooser</code>.
 * 
 * @author Joel Uckelman
 */
public abstract class FileChooser {
  protected Component parent;
  protected DirectoryConfigurer prefs;
  public final static int APPROVE_OPTION = JFileChooser.APPROVE_OPTION;
  public final static int CANCEL_OPTION = JFileChooser.CANCEL_OPTION;
  public final static int ERROR_OPTION = JFileChooser.ERROR_OPTION;
  public final static int FILES_ONLY = JFileChooser.FILES_ONLY;
  public final static int DIRECTORIES_ONLY = JFileChooser.DIRECTORIES_ONLY;

  protected FileChooser(Component parent, DirectoryConfigurer pref) {
    this.parent = parent;
    this.prefs = pref;
  }

  public static FileChooser createFileChooser(Component parent) {
    return createFileChooser(parent, null);
  }

  public static FileChooser createFileChooser(Component parent, DirectoryConfigurer prefs) {
    return createFileChooser(parent, prefs, FILES_ONLY);
  }

  /**
   * Creates a FileChooser appropriate for the user's OS.
   * 
   * @param parent
   *          The Component over which the FileChooser should appear.
   * @param prefs
   *          A FileConfigure that stores the preferred starting directory of the FileChooser in preferences
   */
  public static FileChooser createFileChooser(Component parent, DirectoryConfigurer prefs, int mode) {
    FileChooser fc;
    if (Info.isMacOSX()) {
      // Mac has a good native file dialog
      fc = new NativeFileChooser(parent, prefs, mode);
    }
    else if (mode == FILES_ONLY && Info.isWindows()) {
      // Window has a good native file dialog, but it doesn't support selecting directories
      fc = new NativeFileChooser(parent, prefs, mode);
    }
    else {
      // Linux's native dialog is inferior to Swing's
      fc = new SwingFileChooser(parent, prefs, mode);
    }
    return fc;
  }

  public abstract File getCurrentDirectory();

  public abstract void setCurrentDirectory(File dir);

  public abstract void rescanCurrentDirectory();

  public abstract File getSelectedFile();

  public abstract void setSelectedFile(File file);

  public abstract String getDialogTitle();

  public abstract void setDialogTitle(String title);

  public abstract int showOpenDialog(Component parent);

  public abstract int showSaveDialog(Component parent);

  public abstract FileFilter getFileFilter();

  public abstract void setFileFilter(FileFilter filter);

  public abstract void addChoosableFileFilter(FileFilter filter);

  public abstract boolean removeChoosableFileFilter(FileFilter filter);

  public abstract void resetChoosableFileFilters();

  /**
   * Selects <tt>filename.sav</tt> if <tt>filename.foo</tt> is selected.
   */
  public void selectDotSavFile() {
    File file = getSelectedFile();
    if (file != null) {
      String name = file.getPath();
      if (name != null) {
        int index = name.lastIndexOf('.');
        if (index > 0) {
          name = name.substring(0, index) + ".sav";
          setSelectedFile(new File(name));
        }
      }
    }
  }

  /**
   * Same as {@link #showOpenDialog(Component)}, but uses the <tt>parent</tt> set on creation of this FileDialog.
   */
  public int showOpenDialog() {
    return showOpenDialog(parent);
  }

  /**
   * Same as {@link #showSaveDialog(Component)}, but uses the <tt>parent</tt> set on creation of this FileDialog.
   */
  public int showSaveDialog() {
    return showSaveDialog(parent);
  }

  protected void updateDirectoryPreference() {
    if (prefs != null &&
        getCurrentDirectory() != null &&
        !getCurrentDirectory().equals(prefs.getFileValue())) {
      prefs.setValue(getCurrentDirectory());
    }
  }

  private static class SwingFileChooser extends FileChooser {
    private JFileChooser fc = new JFileChooser();

    public SwingFileChooser(Component parent, DirectoryConfigurer prefs, int mode) {
      super(parent, prefs);
      if (prefs != null && prefs.getFileValue() != null) {
        setCurrentDirectory(prefs.getFileValue());
      }
      if (mode == DIRECTORIES_ONLY) {
        setFileFilter(new DirectoryFileFilter());
      }
      fc.setFileSelectionMode(mode);
    }

    public File getCurrentDirectory() {
      return fc.getCurrentDirectory();
    }

    public void setCurrentDirectory(File dir) {
      fc.setCurrentDirectory(dir);
    }

    public void rescanCurrentDirectory() {
      fc.rescanCurrentDirectory();
    }

    public File getSelectedFile() {
      return fc.getSelectedFile();
    }

    public void setSelectedFile(File file) {
      fc.setSelectedFile(file);
    }

    public int getFileSelectionMode() {
      return fc.getFileSelectionMode();
    }

    public void setFileSelectionMode(int mode) {
      fc.setFileSelectionMode(mode);
    }

    public String getDialogTitle() {
      return fc.getDialogTitle();
    }

    public void setDialogTitle(String title) {
      fc.setDialogTitle(title);
    }

    public int showOpenDialog(Component parent) {
      int value = fc.showOpenDialog(parent);
      updateDirectoryPreference();
      return value;
    }

    public int showSaveDialog(Component parent) {
      int value = fc.showSaveDialog(parent);
      if (value == APPROVE_OPTION
          && getSelectedFile().exists()
          && JOptionPane.NO_OPTION == JOptionPane.showConfirmDialog(parent, "Overwrite " + getSelectedFile().getName() + "?", "File Exists",
              JOptionPane.YES_NO_OPTION)) {
        value = CANCEL_OPTION;
      }
      updateDirectoryPreference();
      return value;
    }

    public FileFilter getFileFilter() {
      javax.swing.filechooser.FileFilter ff = fc.getFileFilter();
      return ff instanceof FileFilter ? (FileFilter) ff : null;
    }

    public void setFileFilter(FileFilter filter) {
      fc.setFileFilter(filter);
    }

    public void addChoosableFileFilter(FileFilter filter) {
      fc.addChoosableFileFilter(filter);
    }

    public boolean removeChoosableFileFilter(FileFilter filter) {
      return fc.removeChoosableFileFilter(filter);
    }

    public void resetChoosableFileFilters() {
      fc.resetChoosableFileFilters();
    }
  }

  private static class NativeFileChooser extends FileChooser {
    private File cur;
    private String title;
    private FileFilter filter;
    private int mode = FILES_ONLY;

    public NativeFileChooser(Component parent, DirectoryConfigurer prefs, int mode) {
      super(parent, prefs);
      if (prefs != null && prefs.getFileValue() != null) {
        setCurrentDirectory(prefs.getFileValue());
      }
      this.mode = mode;
      if (mode == DIRECTORIES_ONLY) {
        setFileFilter(new DirectoryFileFilter());
      }
    }

    public File getCurrentDirectory() {
      return cur == null ? null : cur.getParentFile();
    }

    public void setCurrentDirectory(File dir) {
      cur = dir;
    }

    public void rescanCurrentDirectory() {
    }

    public File getSelectedFile() {
      return cur;
    }

    public void setSelectedFile(File file) {
      cur = file;
    }

    public int getFileSelectionMode() {
      return mode;
    }

    public void setFileSelectionMode(int mode) {
      this.mode = mode;
    }

    public String getDialogTitle() {
      return title;
    }

    public void setDialogTitle(String title) {
      this.title = title;
    }

    protected FileDialog awt_file_dialog_init(Component parent) {
      final FileDialog fd;

      final Window w = parent instanceof Window ? (Window) parent :
        SwingUtilities.getWindowAncestor(parent);

      if (w instanceof Dialog) {
        fd = new FileDialog((Dialog) w, title);
      }
      else if (w instanceof Frame) {
        fd = new FileDialog((Frame) w, title);
      }
      else {
        fd = new FileDialog((Frame) null, title);
      }

      fd.setModal(true);
      fd.setFilenameFilter(filter);
      if (cur != null) {
        if (cur.isDirectory())
          fd.setDirectory(cur.getPath());
        else {
          fd.setDirectory(cur.getParent());
          fd.setFile(cur.getName());
        }
      }
      return fd;
    }

    public int showOpenDialog(Component parent) {
      final FileDialog fd = awt_file_dialog_init(parent);
      fd.setMode(FileDialog.LOAD);
      System.setProperty("apple.awt.fileDialogForDirectories",
                         String.valueOf(mode == DIRECTORIES_ONLY));
      fd.setVisible(true);
        
      final int value;
      if (fd.getFile() != null) {
        cur = new File(fd.getDirectory(), fd.getFile());
        value = FileChooser.APPROVE_OPTION;
      }
      else {
        value = FileChooser.CANCEL_OPTION;
      }
      updateDirectoryPreference();
      return value;
    }

    public int showSaveDialog(Component parent) {
      final FileDialog fd = awt_file_dialog_init(parent);
      fd.setMode(FileDialog.SAVE);
      fd.setVisible(true);
      
      final int value;
      if (fd.getFile() != null) {
        cur = new File(fd.getDirectory(), fd.getFile());
        value = FileChooser.APPROVE_OPTION;
      }
      else {
        value = FileChooser.CANCEL_OPTION;
      }
      updateDirectoryPreference();
      return value;
    }

    public FileFilter getFileFilter() {
      return filter;
    }

    public void setFileFilter(FileFilter filter) {
      this.filter = filter;
    }

    public void addChoosableFileFilter(FileFilter filter) {
    }

    public boolean removeChoosableFileFilter(FileFilter filter) {
      return false;
    }

    public void resetChoosableFileFilters() {
    }
  }
}