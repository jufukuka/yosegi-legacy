/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jp.co.yahoo.yosegi.binary.maker;

import jp.co.yahoo.yosegi.binary.ColumnBinary;
import jp.co.yahoo.yosegi.binary.ColumnBinaryMakerConfig;
import jp.co.yahoo.yosegi.binary.ColumnBinaryMakerCustomConfigNode;
import jp.co.yahoo.yosegi.binary.CompressResultNode;
import jp.co.yahoo.yosegi.blockindex.BlockIndexNode;
import jp.co.yahoo.yosegi.compressor.FindCompressor;
import jp.co.yahoo.yosegi.compressor.ICompressor;
import jp.co.yahoo.yosegi.inmemory.IMemoryAllocator;
import jp.co.yahoo.yosegi.message.objects.DoubleObj;
import jp.co.yahoo.yosegi.message.objects.PrimitiveObject;
import jp.co.yahoo.yosegi.spread.analyzer.IColumnAnalizeResult;
import jp.co.yahoo.yosegi.spread.column.ColumnType;
import jp.co.yahoo.yosegi.spread.column.ICell;
import jp.co.yahoo.yosegi.spread.column.IColumn;
import jp.co.yahoo.yosegi.spread.column.PrimitiveCell;
import jp.co.yahoo.yosegi.spread.column.PrimitiveColumn;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.util.ArrayList;
import java.util.List;

public class DumpDoubleColumnBinaryMaker implements IColumnBinaryMaker {

  private int getBinaryLength( final int columnSize ) {
    return ( Integer.BYTES * 2 ) + columnSize + ( columnSize * Double.BYTES );
  }

  @Override
  public ColumnBinary toBinary(
      final ColumnBinaryMakerConfig commonConfig ,
      final ColumnBinaryMakerCustomConfigNode currentConfigNode ,
      final CompressResultNode compressResultNode ,
      final IColumn column ) throws IOException {
    ColumnBinaryMakerConfig currentConfig = commonConfig;
    if ( currentConfigNode != null ) {
      currentConfig = currentConfigNode.getCurrentConfig();
    }
    byte[] binaryRaw = new byte[ getBinaryLength( column.size() ) ];
    ByteBuffer lengthBuffer = ByteBuffer.wrap( binaryRaw );
    lengthBuffer.putInt( column.size() );
    lengthBuffer.putInt( column.size() * Double.BYTES );

    ByteBuffer nullFlagBuffer = ByteBuffer.wrap( binaryRaw , Integer.BYTES * 2 , column.size() );
    DoubleBuffer doubleBuffer = ByteBuffer.wrap(
        binaryRaw ,
        ( Integer.BYTES * 2 + column.size() ) ,
        ( column.size() * Double.BYTES ) ).asDoubleBuffer();

    int rowCount = 0;
    for ( int i = 0 ; i < column.size() ; i++ ) {
      ICell cell = column.get(i);
      if ( cell.getType() == ColumnType.NULL ) {
        nullFlagBuffer.put( (byte)1 );
        doubleBuffer.put( (double)0 );
      } else {
        rowCount++;
        PrimitiveCell byteCell = (PrimitiveCell) cell;
        nullFlagBuffer.put( (byte)0 );
        doubleBuffer.put( byteCell.getRow().getDouble() );
      }
    }

    byte[] binary = currentConfig.compressorClass.compress( binaryRaw , 0 , binaryRaw.length );

    return new ColumnBinary(
        this.getClass().getName() ,
        currentConfig.compressorClass.getClass().getName() ,
        column.getColumnName() ,
        ColumnType.DOUBLE ,
        rowCount ,
        binaryRaw.length ,
        rowCount * Double.BYTES ,
        -1 ,
        binary ,
        0 ,
        binary.length ,
        null );
  }

  @Override
  public int calcBinarySize( final IColumnAnalizeResult analizeResult ) {
    return getBinaryLength( analizeResult.getColumnSize() );
  }

  @Override
  public IColumn toColumn( final ColumnBinary columnBinary ) throws IOException {
    return new LazyColumn(
        columnBinary.columnName ,
        columnBinary.columnType ,
        new DoubleColumnManager( columnBinary ) );
  }

  @Override
  public void loadInMemoryStorage(
      final ColumnBinary columnBinary ,
      final IMemoryAllocator allocator ) throws IOException {
    loadInMemoryStorage(
        columnBinary , columnBinary.binaryStart , columnBinary.binaryLength , allocator );
  }

  /**
   * Load data into IMemoryAllocator.
   */
  public void loadInMemoryStorage(
      final ColumnBinary columnBinary ,
      final int start ,
      final int length ,
      final IMemoryAllocator allocator ) throws IOException {
    ICompressor compressor = FindCompressor.get( columnBinary.compressorClassName );
    byte[] binary = compressor.decompress( columnBinary.binary , start , length );
    ByteBuffer wrapBuffer = ByteBuffer.wrap( binary );
    int nullFlagBinaryLength = wrapBuffer.getInt();
    int doubleBinaryLength = wrapBuffer.getInt();
    int nullFlagBinaryStart = Integer.BYTES * 2; 
    int doubleBinaryStart = nullFlagBinaryStart + nullFlagBinaryLength;

    ByteBuffer nullFlagBuffer =
        ByteBuffer.wrap( binary , nullFlagBinaryStart , nullFlagBinaryLength );
    DoubleBuffer doubleBuffer = ByteBuffer.wrap(
        binary ,
        doubleBinaryStart ,
        doubleBinaryLength ).asDoubleBuffer();
    for ( int i = 0 ; i < nullFlagBinaryLength ; i++ ) {
      if ( nullFlagBuffer.get() == (byte)0 ) {
        allocator.setDouble( i , doubleBuffer.get( i ) );
      }
    }
    allocator.setValueCount( nullFlagBinaryLength );
  }

  @Override
  public void setBlockIndexNode(
      final BlockIndexNode parentNode ,
      final ColumnBinary columnBinary ,
      final int spreadIndex ) throws IOException {
    parentNode.getChildNode( columnBinary.columnName ).disable();
  }

  public class DoubleDicManager implements IDicManager {

    private final byte[] nullBuffer;
    private final int nullBufferStart;
    private final int nullBufferLength;
    private final DoubleBuffer dicBuffer;

    /**
     * Initialize by setting Null flag and Double buffer.
     */
    public DoubleDicManager(
        final byte[] nullBuffer ,
        final int nullBufferStart ,
        final int nullBufferLength ,
        final DoubleBuffer dicBuffer ) {
      this.nullBuffer = nullBuffer;
      this.nullBufferStart = nullBufferStart;
      this.nullBufferLength = nullBufferLength;
      this.dicBuffer = dicBuffer;
    }

    @Override
    public PrimitiveObject get( final int index ) throws IOException {
      if ( nullBuffer[index + nullBufferStart] == (byte)1 ) {
        return null;
      }
      return new DoubleObj( dicBuffer.get( index ) );
    }

    @Override
    public int getDicSize() throws IOException {
      return nullBufferLength;
    }

  }

  public class DoubleColumnManager implements IColumnManager {

    private final ColumnBinary columnBinary;
    private final int binaryStart;
    private final int binaryLength;
    private PrimitiveColumn column;
    private boolean isCreate;

    /**
     * Initialize by setting binary.
     */
    public DoubleColumnManager( final ColumnBinary columnBinary ) throws IOException {
      this.columnBinary = columnBinary;
      this.binaryStart = columnBinary.binaryStart;
      this.binaryLength = columnBinary.binaryLength;
    }

    /**
     * Initialize by setting binary.
     */
    public DoubleColumnManager(
        final ColumnBinary columnBinary ,
        final int binaryStart ,
        final int binaryLength ) throws IOException {
      this.columnBinary = columnBinary;
      this.binaryStart = binaryStart;
      this.binaryLength = binaryLength;
    }

    private void create() throws IOException {
      if ( isCreate ) {
        return;
      }

      ICompressor compressor = FindCompressor.get( columnBinary.compressorClassName );
      byte[] binary = compressor.decompress( columnBinary.binary , binaryStart , binaryLength );
      ByteBuffer wrapBuffer = ByteBuffer.wrap( binary );
      int nullFlagBinaryLength = wrapBuffer.getInt();
      int doubleBinaryLength = wrapBuffer.getInt();
      int nullFlagBinaryStart = Integer.BYTES * 2;
      int doubleBinaryStart = nullFlagBinaryStart + nullFlagBinaryLength;

      DoubleBuffer doubleBuffer = ByteBuffer.wrap(
          binary , doubleBinaryStart , doubleBinaryLength ).asDoubleBuffer();

      IDicManager dicManager = new DoubleDicManager(
          binary , nullFlagBinaryStart , nullFlagBinaryLength , doubleBuffer );
      column = new PrimitiveColumn( columnBinary.columnType , columnBinary.columnName );
      column.setCellManager( new BufferDirectCellManager(
          ColumnType.DOUBLE , dicManager , nullFlagBinaryLength ) );

      isCreate = true;
    }

    @Override
    public IColumn get() {
      try {
        create();
      } catch ( IOException ex ) {
        throw new UncheckedIOException( ex );
      }
      return column;
    }

    @Override
    public List<String> getColumnKeys() {
      return new ArrayList<String>();
    }

    @Override
    public int getColumnSize() {
      return 0;
    }

  }

}
