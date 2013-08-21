create or replace procedure DK234.sum_and_diff_of_two_numbers
  (a in number,
   b in number,
   c out number,
   d out number)
is
begin
  c := a + b;
  d := a - b;
end;
/