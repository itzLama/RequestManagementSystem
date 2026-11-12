import { notFound } from "next/navigation";
import { RichTextPoc } from "@/components/dev/rich-text/RichTextPoc";

export default function RichTextPocPage() {
  if (process.env.NODE_ENV !== "development") notFound();
  return <RichTextPoc />;
}

