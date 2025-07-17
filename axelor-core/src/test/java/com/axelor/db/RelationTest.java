/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2005-2025 Axelor (<http://axelor.com>).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.axelor.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.fail;

import com.axelor.JpaTest;
import com.axelor.test.db.Invoice;
import com.axelor.test.db.Move;
import com.axelor.test.db.MoveLine;
import com.axelor.test.db.repo.InvoiceRepository;
import com.axelor.test.db.repo.MoveRepository;
import com.google.common.collect.Lists;
import com.google.inject.persist.Transactional;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class RelationTest extends JpaTest {

  @Inject private InvoiceRepository invoices;

  @Inject private MoveRepository moves;

  @BeforeEach
  @Transactional
  public void createInvoice() {
    Invoice invoice = new Invoice();
    invoices.save(invoice);
  }

  @Transactional
  protected void testErrors() {
    Invoice invoice = invoices.all().fetchOne();

    Move move = new Move();
    move.setMoveLines(Lists.<MoveLine>newArrayList());
    move.setInvoice(invoice);

    MoveLine line1 = new MoveLine();
    line1.setCredit(new BigDecimal("20"));
    line1.setDebit(BigDecimal.ZERO);
    line1.setMove(move);
    line1.setInvoiceReject(invoice);

    move.getMoveLines().add(line1);

    invoice.setRejectMoveLine(line1);
    invoice.setMove(move);

    invoices.save(invoice);

    assertSame(line1, invoice.getRejectMoveLine());
    assertSame(line1, move.getMoveLines().get(0));

    assertEquals(new BigDecimal("20"), line1.getCredit());
    assertEquals(BigDecimal.ZERO, line1.getDebit());

    moves.save(move);
  }

  @Transactional
  protected void testRelations() {
    Move move = all(Move.class).fetchOne();
    MoveLine line = all(MoveLine.class).fetchOne();
    Invoice invoice = all(Invoice.class).fetchOne();

    assertSame(move, line.getMove());
    assertSame(move, invoice.getMove());
    assertSame(line, invoice.getRejectMoveLine());

    assertSame(line, move.getMoveLines().get(0));
  }

  @Transactional
  protected void testRemoveCollection() {
    Move move = all(Move.class).fetchOne();

    // the moveLines is exclusive o2m field, so clear would delete all
    // the move lines but one of move line is referenced outside so the
    // entity manager will throws an exception
    move.getMoveLines().clear();
  }

  @Transactional
  protected void testRemoveCollectionProper() {
    Move move = all(Move.class).fetchOne();
    Invoice invoice = all(Invoice.class).fetchOne();

    // clear the reference
    invoice.setRejectMoveLine(null);

    // then clear the collection
    move.getMoveLines().clear();

    // or

    // invoices.remove(invoice); // this will auto detach invoice from all it's children
    // move.getMoveLines().clear();
  }

  @Test
  public void test() {
    testErrors();
    testRelations();

    try {
      testRemoveCollection();
      fail();
    } catch (PersistenceException e) {
    }

    testRemoveCollectionProper();
  }
}
